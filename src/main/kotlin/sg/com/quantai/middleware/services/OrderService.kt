package sg.com.quantai.middleware.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import sg.com.quantai.middleware.data.mongo.Order
import sg.com.quantai.middleware.data.mongo.enums.AssetType
import sg.com.quantai.middleware.data.mongo.enums.OrderSide
import sg.com.quantai.middleware.data.mongo.enums.OrderStatus
import sg.com.quantai.middleware.data.mongo.enums.OrderType
import sg.com.quantai.middleware.repositories.mongo.OrderRepository
import sg.com.quantai.middleware.repositories.mongo.PortfolioRepository
import sg.com.quantai.middleware.requests.CreateOrderRequest
import sg.com.quantai.middleware.requests.OrderFillRequest
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val portfolioRepository: PortfolioRepository,
    private val referencePriceService: ReferencePriceService,
    private val bookingService: PortfolioBookingService,
    private val restTemplate: RestTemplate,
    @Value("\${quantai.ems.url:http://localhost:10030}") private val emsUrl: String,
) {
    private val log = LoggerFactory.getLogger(OrderService::class.java)

    fun createOrder(userId: String, request: CreateOrderRequest): Order {
        val portfolio = portfolioRepository.findOneByUid(request.portfolioId)

        val assetType = request.assetType ?: inferAssetType(request.symbol)
        val referencePrice = referencePriceService.latestPrice(request.symbol)

        // Server-side validation (send nothing to EMS on failure).
        validate(request, assetType, portfolio, referencePrice)

        val order = Order(
            userId = userId,
            portfolioId = request.portfolioId,
            symbol = request.symbol.trim().uppercase(),
            assetType = assetType,
            name = request.name,
            side = request.side,
            quantity = request.quantity,
            orderType = request.orderType,
            limitPrice = request.limitPrice,
            referencePrice = referencePrice?.price,
        )

        val saved = orderRepository.save(order)
        log.info("Order {} created ({} {} {} {}), submitting to EMS", saved.uid, saved.side, saved.quantity, saved.symbol, saved.orderType)

        return try {
            submitToEms(saved)
            saved
        } catch (e: Exception) {
            log.warn("EMS submission failed for order {}: {}", saved.uid, e.message)
            val rejected = saved.copy(status = OrderStatus.REJECTED, rejectReason = "Trading unavailable", updatedAt = LocalDateTime.now())
            orderRepository.save(rejected)
            rejected
        }
    }

    private fun validate(
        request: CreateOrderRequest,
        assetType: AssetType,
        portfolio: sg.com.quantai.middleware.data.mongo.Portfolio,
        referencePrice: ReferencePrice?,
    ) {
        if (request.quantity <= BigDecimal.ZERO) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be greater than zero")
        }

        when (request.side) {
            OrderSide.BUY -> {
                val effectivePrice = when (request.orderType) {
                    OrderType.LIMIT -> request.limitPrice
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit order requires a price")
                    OrderType.MARKET -> referencePrice?.price
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Price unavailable")
                }
                val requiredCash = request.quantity.multiply(effectivePrice)
                if (portfolio.cashBalance < requiredCash) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds")
                }
            }
            OrderSide.SELL -> {
                val holdings = bookingService.holdings(portfolio, request.name ?: request.symbol)
                if (holdings < request.quantity) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient holdings")
                }
            }
        }
    }

    private fun submitToEms(order: Order) {
        val body = mapOf(
            "SYMBOL" to order.symbol,
            "SIDE" to order.side.name,
            "QUANTITY" to order.quantity,
            "ORDERTYPE" to order.orderType.name,
            "ORDERID" to order.uid,
            "LIMITPRICE" to order.limitPrice,
        )
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(body, headers)

        val response = restTemplate.postForEntity("$emsUrl/execution/orders", entity, String::class.java)
        if (!response.statusCode.is2xxSuccessful) {
            throw IllegalStateException("EMS returned ${response.statusCode}")
        }
    }

    fun getOrder(orderId: String): Order? = orderRepository.findByUid(orderId)

    fun getOrdersByPortfolio(portfolioId: String): List<Order> =
        orderRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioId)

    fun handleFill(request: OrderFillRequest) {
        val orderId = request.orderId ?: run {
            log.warn("Fill received without orderId (ClOrdID); ignoring")
            return
        }
        val order = orderRepository.findByUid(orderId) ?: run {
            log.warn("Fill received for unknown order {}; ignoring", orderId)
            return
        }
        val execId = request.execId ?: run {
            log.warn("Fill received without execId for order {}; ignoring", orderId)
            return
        }

        // Idempotency: a fill must never be booked twice.
        if (order.processedExecIds.contains(execId)) {
            log.info("Fill {} for order {} already processed; ignoring", execId, orderId)
            return
        }

        val updatedExecIds = (order.processedExecIds + execId).toMutableSet()

        val execType = request.execType
        val ordStatus = request.ordStatus

        // Rejected / cancel-rejected -> no portfolio change.
        if (execType == "8" || execType == "9" || ordStatus == "8") {
            val rejected = order.copy(
                status = OrderStatus.REJECTED,
                rejectReason = request.text ?: "Rejected by exchange",
                processedExecIds = updatedExecIds,
                updatedAt = LocalDateTime.now(),
            )
            orderRepository.save(rejected)
            log.info("Order {} rejected: {}", orderId, rejected.rejectReason)
            return
        }

        // Filled / partially filled -> book the incremental quantity.
        val incrementalQty = request.lastQuantity
            ?: request.cumulativeQuantity?.subtract(order.filledQty)
            ?: BigDecimal.ZERO
        val incrementalPrice = request.lastPrice ?: request.averagePrice

        if (incrementalQty > BigDecimal.ZERO && incrementalPrice != null) {
            bookingService.bookTrade(
                userId = order.userId,
                portfolioId = order.portfolioId,
                assetType = order.assetType,
                name = order.name,
                symbol = order.symbol,
                side = order.side,
                quantity = incrementalQty,
                price = incrementalPrice,
            )
        }

        val newFilledQty = order.filledQty.add(incrementalQty)
        val leaves = request.leavesQuantity
        val fullyFilled = (ordStatus == "2") || (leaves != null && leaves <= BigDecimal.ZERO)
        val newStatus = if (fullyFilled) OrderStatus.FILLED else OrderStatus.PARTIALLY_FILLED

        val updated = order.copy(
            status = newStatus,
            filledQty = newFilledQty,
            avgPx = request.averagePrice ?: incrementalPrice ?: order.avgPx,
            processedExecIds = updatedExecIds,
            updatedAt = LocalDateTime.now(),
        )
        orderRepository.save(updated)
        log.info("Order {} -> {} (filledQty={}, avgPx={})", orderId, newStatus, newFilledQty, updated.avgPx)
    }

    private fun inferAssetType(symbol: String): AssetType {
        return when {
            symbol.contains("/") -> AssetType.FOREX
            else -> AssetType.STOCK
        }
    }
}
