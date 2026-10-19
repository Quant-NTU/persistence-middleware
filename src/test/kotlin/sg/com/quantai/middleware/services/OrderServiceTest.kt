package sg.com.quantai.middleware.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import sg.com.quantai.middleware.data.mongo.Order
import sg.com.quantai.middleware.data.mongo.Portfolio
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.data.mongo.enums.AssetType
import sg.com.quantai.middleware.data.mongo.enums.OrderSide
import sg.com.quantai.middleware.data.mongo.enums.OrderStatus
import sg.com.quantai.middleware.data.mongo.enums.OrderType
import sg.com.quantai.middleware.repositories.mongo.OrderRepository
import sg.com.quantai.middleware.repositories.mongo.PortfolioRepository
import sg.com.quantai.middleware.requests.CreateOrderRequest
import sg.com.quantai.middleware.requests.OrderFillRequest
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.Instant

class OrderServiceTest {

    private fun service(
        orderRepository: OrderRepository,
        portfolioRepository: PortfolioRepository,
        referencePriceService: ReferencePriceService,
        bookingService: PortfolioBookingService,
        restTemplate: RestTemplate,
    ) = OrderService(
        orderRepository,
        portfolioRepository,
        referencePriceService,
        bookingService,
        restTemplate,
        "http://localhost:10030",
    )

    private fun portfolio(cash: String): Portfolio =
        Portfolio(description = "d", name = "n", cashBalance = BigDecimal(cash), owner = User(name = "u", email = "e@x.com"))

    @Test
    fun `buy with insufficient funds throws 400`() {
        val orderRepository = mock<OrderRepository>()
        val portfolioRepository = mock<PortfolioRepository>()
        val referencePriceService = mock<ReferencePriceService>()
        val bookingService = mock<PortfolioBookingService>()
        val restTemplate = mock<RestTemplate>()
        val svc = service(orderRepository, portfolioRepository, referencePriceService, bookingService, restTemplate)

        whenever(portfolioRepository.findOneByUid("p1")).thenReturn(portfolio("100"))
        whenever(referencePriceService.latestPrice("AAPL"))
            .thenReturn(ReferencePrice("AAPL", BigDecimal("350"), Instant.now()))

        val request = CreateOrderRequest(
            portfolioId = "p1",
            symbol = "AAPL",
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            orderType = OrderType.MARKET,
        )

        val ex = assertThrows(ResponseStatusException::class.java) { svc.createOrder("u1", request) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Insufficient funds", ex.reason)
        verify(orderRepository, times(0)).save(any())
    }

    @Test
    fun `market buy with no reference price throws price unavailable`() {
        val orderRepository = mock<OrderRepository>()
        val portfolioRepository = mock<PortfolioRepository>()
        val referencePriceService = mock<ReferencePriceService>()
        val bookingService = mock<PortfolioBookingService>()
        val restTemplate = mock<RestTemplate>()
        val svc = service(orderRepository, portfolioRepository, referencePriceService, bookingService, restTemplate)

        whenever(portfolioRepository.findOneByUid("p1")).thenReturn(portfolio("100000"))
        whenever(referencePriceService.latestPrice("AAPL")).thenReturn(null)

        val request = CreateOrderRequest(
            portfolioId = "p1",
            symbol = "AAPL",
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            orderType = OrderType.MARKET,
        )

        val ex = assertThrows(ResponseStatusException::class.java) { svc.createOrder("u1", request) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        assertEquals("Price unavailable", ex.reason)
    }

    @Test
    fun `duplicate fill with same exec id is booked once`() {
        val orderRepository = mock<OrderRepository>()
        val portfolioRepository = mock<PortfolioRepository>()
        val referencePriceService = mock<ReferencePriceService>()
        val bookingService = mock<PortfolioBookingService>()
        val restTemplate = mock<RestTemplate>()
        val svc = service(orderRepository, portfolioRepository, referencePriceService, bookingService, restTemplate)

        var stored: Order = Order(
            userId = "u1",
            portfolioId = "p1",
            symbol = "AAPL",
            assetType = AssetType.STOCK,
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            orderType = OrderType.MARKET,
        )
        whenever(orderRepository.findByUid("o1")).thenAnswer { stored }
        whenever(orderRepository.save(any<Order>())).thenAnswer { inv ->
            stored = inv.getArgument(0)
            stored
        }
        whenever(bookingService.bookTrade(any(), any(), any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(portfolio("100"))

        val fill = OrderFillRequest(
            orderId = "o1",
            execId = "e1",
            execType = "2",
            ordStatus = "2",
            cumulativeQuantity = BigDecimal("10"),
            leavesQuantity = BigDecimal.ZERO,
            averagePrice = BigDecimal("100"),
            lastPrice = BigDecimal("100"),
            lastQuantity = BigDecimal("10"),
        )

        svc.handleFill(fill)
        svc.handleFill(fill) // duplicate

        verify(bookingService, times(1)).bookTrade(any(), any(), any(), anyOrNull(), any(), any(), any(), any())
        assertEquals(OrderStatus.FILLED, stored.status)
    }

    @Test
    fun `fill marks order filled and books quantity`() {
        val orderRepository = mock<OrderRepository>()
        val portfolioRepository = mock<PortfolioRepository>()
        val referencePriceService = mock<ReferencePriceService>()
        val bookingService = mock<PortfolioBookingService>()
        val restTemplate = mock<RestTemplate>()
        val svc = service(orderRepository, portfolioRepository, referencePriceService, bookingService, restTemplate)

        var stored: Order = Order(
            userId = "u1",
            portfolioId = "p1",
            symbol = "AAPL",
            assetType = AssetType.STOCK,
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            orderType = OrderType.MARKET,
        )
        whenever(orderRepository.findByUid("o1")).thenAnswer { stored }
        whenever(orderRepository.save(any<Order>())).thenAnswer { inv ->
            stored = inv.getArgument(0)
            stored
        }
        whenever(bookingService.bookTrade(any(), any(), any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(portfolio("100"))

        svc.handleFill(
            OrderFillRequest(
                orderId = "o1",
                execId = "e1",
                execType = "2",
                ordStatus = "2",
                cumulativeQuantity = BigDecimal("10"),
                leavesQuantity = BigDecimal.ZERO,
                averagePrice = BigDecimal("100"),
                lastPrice = BigDecimal("100"),
                lastQuantity = BigDecimal("10"),
            )
        )

        assertEquals(OrderStatus.FILLED, stored.status)
        assertEquals(BigDecimal("10"), stored.filledQty)
        assertEquals(BigDecimal("100"), stored.avgPx)
    }

    @Test
    fun `ems unavailable marks order rejected`() {
        val orderRepository = mock<OrderRepository>()
        val portfolioRepository = mock<PortfolioRepository>()
        val referencePriceService = mock<ReferencePriceService>()
        val bookingService = mock<PortfolioBookingService>()
        val restTemplate = mock<RestTemplate>()
        val svc = service(orderRepository, portfolioRepository, referencePriceService, bookingService, restTemplate)

        whenever(portfolioRepository.findOneByUid("p1")).thenReturn(portfolio("100000"))
        whenever(referencePriceService.latestPrice("AAPL"))
            .thenReturn(ReferencePrice("AAPL", BigDecimal("350"), Instant.now()))
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.getArgument(0) }
        whenever(restTemplate.postForEntity(any<String>(), any(), any<Class<String>>()))
            .thenThrow(RuntimeException("connection refused"))

        val request = CreateOrderRequest(
            portfolioId = "p1",
            symbol = "AAPL",
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            orderType = OrderType.MARKET,
        )

        val order = svc.createOrder("u1", request)
        assertEquals(OrderStatus.REJECTED, order.status)
        assertEquals("Trading unavailable", order.rejectReason)
    }
}
