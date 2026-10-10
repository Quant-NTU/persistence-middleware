
package sg.com.quantai.middleware.controllers.mongo

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import sg.com.quantai.middleware.data.mongo.*
import sg.com.quantai.middleware.data.mongo.enums.PortfolioActionEnum
import sg.com.quantai.middleware.repositories.mongo.*
import sg.com.quantai.middleware.requests.PortfolioRequest
import sg.com.quantai.middleware.requests.assets.CryptoRequest
import sg.com.quantai.middleware.requests.assets.ForexRequest
import sg.com.quantai.middleware.requests.assets.StockRequest
import java.math.BigDecimal
import java.time.LocalDateTime

@RestController
@RequestMapping("/portfolios")
class PortfolioController(
    private val portfolioRepository: PortfolioRepository,
    private val portfolioHistoryRepository: PortfolioHistoryRepository,
    private val cryptoRepository: CryptoRepository,
    private val stockRepository: StockRepository,
    private val forexRepository: ForexRepository,
    private val userRepository: UserRepository
) {
    private val logger =
        LoggerFactory.getLogger(PortfolioController::class.java)

    // ============================================================
    // SECURITY HELPERS
    // ============================================================

    private fun ownUser(jwt: Jwt, userId: String) {
        if (jwt.subject != userId) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Cannot access another user's resources"
            )
        }
    }

    private fun ownPortfolio(
        jwt: Jwt,
        portfolioId: String
    ): Portfolio {
        val portfolio = try {
            portfolioRepository.findOneByUid(portfolioId)
        } catch (e: Exception) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Portfolio not found"
            )
        }

        if (portfolio.owner.uid != jwt.subject) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Portfolio not found"
            )
        }

        return portfolio
    }

    private fun positive(amount: BigDecimal, name: String) {
        if (amount <= BigDecimal.ZERO) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "$name must be positive"
            )
        }
    }

    // ============================================================
    // ASSET HELPERS
    // ============================================================

    private fun assetQuantity(
        asset: Asset,
        portfolio: Portfolio
    ): BigDecimal {
        return portfolioHistoryRepository
            .findByPortfolio(portfolio)
            .filter {
                it.asset.name == asset.name &&
                    it.asset::class == asset::class
            }
            .fold(BigDecimal.ZERO) { total, history ->
                when (history.action) {
                    PortfolioActionEnum.ADD_MANUAL_ASSET,
                    PortfolioActionEnum.BUY_REAL_ASSET -> {
                        total + history.quantity
                    }

                    PortfolioActionEnum.REMOVE_MANUAL_ASSET -> {
                        total - history.quantity
                    }

                    PortfolioActionEnum.SELL_REAL_ASSET -> {
                        total - history.quantity.abs()
                    }

                    else -> total
                }
            }
    }

    fun checkPortfolioAssetQuantity(
        quantity: BigDecimal,
        asset: Asset,
        portfolio: Portfolio
    ): Boolean {
        return quantity > BigDecimal.ZERO &&
            assetQuantity(asset, portfolio) >= quantity
    }

    private fun assetFor(
        type: String,
        name: String
    ): Asset {
        return try {
            when (type.lowercase()) {
                "stock" -> stockRepository.findByName(name)
                "crypto" -> cryptoRepository.findByName(name)
                "forex" -> forexRepository.findByName(name)
                else -> throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid asset type"
                )
            }
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: Exception) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Asset not found"
            )
        }
    }

    private fun record(
        asset: Asset,
        action: PortfolioActionEnum,
        quantity: BigDecimal,
        value: BigDecimal,
        portfolio: Portfolio
    ) {
        portfolioHistoryRepository.save(
            PortfolioHistory(
                asset = asset,
                action = action,
                quantity = quantity,
                value = value,
                portfolio = portfolio
            )
        )
    }

    // ============================================================
    // PORTFOLIO VIEWING
    // ============================================================

    @GetMapping("/user/{user_id}")
    fun getAllPortfoliosByUser(
        @PathVariable("user_id") userId: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        ownUser(jwt, userId)

        val user = userRepository.findOneByUid(userId)

        if (!portfolioRepository.existsByOwnerAndMain(user, true)) {
            portfolioRepository.save(
                Portfolio(
                    main = true,
                    description = "Default portfolio",
                    name = "Default Portfolio",
                    owner = user
                )
            )
        }

        return ResponseEntity.ok(
            portfolioRepository.findByOwner(user)
        )
    }

    data class PortfolioHistoryResponse(
        val history: PortfolioHistory,
        val assetType: String
    )

    @GetMapping("/history/{user_id}/{portfolio_id}")
    fun getAllPortfolioHistoryByPortfolio(
        @PathVariable("user_id") userId: String,
        @PathVariable("portfolio_id") portfolioId: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<List<PortfolioHistoryResponse>> {
        ownUser(jwt, userId)

        val history = portfolioHistoryRepository.findByPortfolio(
            ownPortfolio(jwt, portfolioId)
        )

        return ResponseEntity.ok(
            history.map {
                PortfolioHistoryResponse(
                    it,
                    when (it.asset) {
                        is Forex -> "Forex"
                        is Crypto -> "Crypto"
                        is Stock -> "Stock"
                        else -> "Unknown"
                    }
                )
            }
        )
    }

    // ============================================================
    // PORTFOLIO CREATION AND EDITING
    // ============================================================

    @PostMapping("/{user_id}")
    fun createPortfolio(
        @PathVariable("user_id") userId: String,
        @RequestBody request: PortfolioRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Portfolio> {
        ownUser(jwt, userId)

        val user = userRepository.findOneByUid(userId)

        val saved = portfolioRepository.save(
            Portfolio(
                description = request.description,
                name = request.name,
                cashBalance = BigDecimal.ZERO,
                owner = user
            )
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @PutMapping("/{user_id}/{portfolio_id}")
    fun updatePortfolio(
        @PathVariable("user_id") userId: String,
        @PathVariable("portfolio_id") portfolioId: String,
        @RequestBody request: PortfolioRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Portfolio> {
        ownUser(jwt, userId)

        val portfolio = ownPortfolio(jwt, portfolioId)

        val updated = portfolioRepository.save(
            portfolio.copy(
                description = request.description,
                name = request.name,
                updatedDate = LocalDateTime.now()
            )
        )

        return ResponseEntity.ok(updated)
    }

    // ============================================================
    // MANUAL ASSET ADDITION
    // ============================================================

    @PostMapping("/asset/crypto/{portfolio_id}")
    fun addCrypto(
        @PathVariable("portfolio_id") portfolioId: String,
        @RequestBody request: CryptoRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Crypto> {
        val portfolio = ownPortfolio(jwt, portfolioId)

        if (request.portfolio_uid != portfolioId) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Portfolio IDs do not match"
            )
        }

        positive(request.quantity, "Quantity")
        positive(request.purchasePrice, "Purchase price")

        if (!cryptoRepository.existsByName(request.name)) {
            cryptoRepository.save(
                Crypto(
                    name = request.name,
                    quantity = request.quantity,
                    purchasePrice = request.purchasePrice,
                    symbol = request.symbol
                )
            )
        }

        val crypto = cryptoRepository.findByName(request.name)

        record(
            crypto,
            PortfolioActionEnum.ADD_MANUAL_ASSET,
            request.quantity,
            request.quantity * request.purchasePrice,
            portfolio
        )

        return ResponseEntity.ok(crypto)
    }

    @PostMapping("/asset/stock/{portfolio_id}")
    fun addStock(
        @PathVariable("portfolio_id") portfolioId: String,
        @RequestBody request: StockRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Stock> {
        val portfolio = ownPortfolio(jwt, portfolioId)

        if (request.portfolio_uid != portfolioId) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Portfolio IDs do not match"
            )
        }

        positive(request.quantity, "Quantity")
        positive(request.purchasePrice, "Purchase price")

        if (!stockRepository.existsByName(request.name)) {
            stockRepository.save(
                Stock(
                    name = request.name,
                    quantity = request.quantity,
                    purchasePrice = request.purchasePrice,
                    ticker = request.ticker
                )
            )
        }

        val stock = stockRepository.findByName(request.name)

        record(
            stock,
            PortfolioActionEnum.ADD_MANUAL_ASSET,
            request.quantity,
            request.quantity * request.purchasePrice,
            portfolio
        )

        return ResponseEntity.ok(stock)
    }

    @PostMapping("/asset/forex/{portfolio_id}")
    fun addForex(
        @PathVariable("portfolio_id") portfolioId: String,
        @RequestBody request: ForexRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Forex> {
        val portfolio = ownPortfolio(jwt, portfolioId)

        if (request.portfolio_uid != portfolioId) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Portfolio IDs do not match"
            )
        }

        positive(request.quantity, "Quantity")
        positive(request.purchasePrice, "Purchase price")

        if (!forexRepository.existsByName(request.name)) {
            forexRepository.save(
                Forex(
                    name = request.name,
                    quantity = request.quantity,
                    purchasePrice = request.purchasePrice,
                    currencyPair = request.currencyPair
                )
            )
        }

        val forex = forexRepository.findByName(request.name)

        record(
            forex,
            PortfolioActionEnum.ADD_MANUAL_ASSET,
            request.quantity,
            request.quantity * request.purchasePrice,
            portfolio
        )

        return ResponseEntity.ok(forex)
    }

    // ============================================================
    // ASSET TRANSFERS
    // ============================================================

    
    private fun transfer(
       jwt: Jwt,
       userId: String,
       portfolioId: String,
       asset: Asset,
       quantity: BigDecimal,
       price: BigDecimal,
       action: String?
   ): ResponseEntity<Any> {

        ownUser(jwt, userId)
        positive(quantity, "Quantity")
        positive(price, "Purchase price")

        if (action != "Add" && action != "Remove") {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Action must be Add or Remove"
            )
        }

        val target = ownPortfolio(jwt, portfolioId)
        val user = userRepository.findOneByUid(userId)
        val main = portfolioRepository.findByOwnerAndMain(user, true)

        if (target.uid == main.uid) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot transfer assets to the same portfolio"
            )
        }

        val source = if (action == "Add") main else target
        val destination = if (action == "Add") target else main

        if (!checkPortfolioAssetQuantity(quantity, asset, source)) {
            return ResponseEntity.badRequest()
                .body("Insufficient asset quantity in source portfolio")
        }

        val value = quantity * price

        record(
            asset,
            PortfolioActionEnum.REMOVE_MANUAL_ASSET,
            quantity,
            value,
            source
        )

        record(
            asset,
            PortfolioActionEnum.ADD_MANUAL_ASSET,
            quantity,
            value,
            destination
        )

        return ResponseEntity.ok(
            "Transferred $quantity from ${source.name} to ${destination.name}."
        )
    }

    @PostMapping("/asset/stock/update/{user_id}")
    fun updateStock(
        @PathVariable("user_id") userId: String,
        @RequestBody request: StockRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        return transfer(
            jwt,
            userId,
            request.portfolio_uid,
            assetFor("stock", request.name),
            request.quantity,
            request.purchasePrice,
            request.action
        )
    }

    @PostMapping("/asset/crypto/update/{user_id}")
    fun updateCrypto(
        @PathVariable("user_id") userId: String,
        @RequestBody request: CryptoRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        return transfer(
            jwt,
            userId,
            request.portfolio_uid,
            assetFor("crypto", request.name),
            request.quantity,
            request.purchasePrice,
            request.action
        )
    }

    @PostMapping("/asset/forex/update/{user_id}")
    fun updateForex(
        @PathVariable("user_id") userId: String,
        @RequestBody request: ForexRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        return transfer(
            jwt,
            userId,
            request.portfolio_uid,
            assetFor("forex", request.name),
            request.quantity,
            request.purchasePrice,
            request.action
        )
    }

    // ============================================================
    // PORTFOLIO DELETION
    // ============================================================

    @DeleteMapping("/user/{user_id}/{portfolio_id}")
    fun deletePortfolioFromUser(
        @PathVariable("user_id") userId: String,
        @PathVariable("portfolio_id") portfolioId: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        ownUser(jwt, userId)

        val portfolio = ownPortfolio(jwt, portfolioId)

        if (portfolio.main) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Cannot delete main portfolio")
        }

        val user = userRepository.findOneByUid(userId)
        val main = portfolioRepository.findByOwnerAndMain(user, true)

        portfolioHistoryRepository.findByPortfolio(portfolio)
            .forEach {
                portfolioHistoryRepository.save(
                    it.copy(
                        portfolio = main,
                        updatedDate = LocalDateTime.now()
                    )
                )
            }

        portfolioRepository.deleteByUid(portfolioId)

        return ResponseEntity.ok(
            "Deleted portfolio ${portfolio.name}"
        )
    }

    // ============================================================
    // BUY ASSET
    // ============================================================

    data class BuyAssetRequest(
        val assetType: String,
        val symbol: String,
        val name: String,
        val quantity: BigDecimal,
        val pricePerUnit: BigDecimal,
        val transactionDate: String
    )

    @PostMapping("/buy/{user_id}/{portfolio_id}")
    fun buyAsset(
        @PathVariable("user_id") userId: String,
        @PathVariable("portfolio_id") portfolioId: String,
        @RequestBody request: BuyAssetRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        ownUser(jwt, userId)

        val portfolio = ownPortfolio(jwt, portfolioId)

        positive(request.quantity, "Quantity")
        positive(request.pricePerUnit, "Price")

        val totalCost = request.quantity * request.pricePerUnit

        if (portfolio.cashBalance < totalCost) {
            return ResponseEntity.badRequest()
                .body("Insufficient cash balance")
        }

        val asset: Asset = when (request.assetType.lowercase()) {
            "stock" -> {
                if (!stockRepository.existsByName(request.name)) {
                    stockRepository.save(
                        Stock(
                            name = request.name,
                            quantity = request.quantity,
                            purchasePrice = request.pricePerUnit,
                            ticker = request.symbol
                        )
                    )
                }
                stockRepository.findByName(request.name)
            }

            "crypto" -> {
                if (!cryptoRepository.existsByName(request.name)) {
                    cryptoRepository.save(
                        Crypto(
                            name = request.name,
                            quantity = request.quantity,
                            purchasePrice = request.pricePerUnit,
                            symbol = request.symbol
                        )
                    )
                }
                cryptoRepository.findByName(request.name)
            }

            "forex" -> {
                if (!forexRepository.existsByName(request.name)) {
                    forexRepository.save(
                        Forex(
                            name = request.name,
                            quantity = request.quantity,
                            purchasePrice = request.pricePerUnit,
                            currencyPair = request.symbol
                        )
                    )
                }
                forexRepository.findByName(request.name)
            }

            else -> {
                return ResponseEntity.badRequest()
                    .body("Invalid asset type")
            }
        }

        record(
            asset,
            PortfolioActionEnum.BUY_REAL_ASSET,
            request.quantity,
            totalCost,
            portfolio
        )

        val updated = portfolioRepository.save(
            portfolio.copy(
                cashBalance = portfolio.cashBalance - totalCost,
                updatedDate = LocalDateTime.now()
            )
        )

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "portfolio" to updated,
                "message" to "Successfully bought ${request.quantity} of ${request.name}"
            )
        )
    }

    // ============================================================
    // SELL ASSET
    // ============================================================

    data class SellAssetRequest(
        val assetType: String,
        val name: String,
        val quantity: BigDecimal,
        val pricePerUnit: BigDecimal
    )

    @PostMapping("/sell/{user_id}/{portfolio_id}")
    fun sellAsset(
        @PathVariable("user_id") userId: String,
        @PathVariable("portfolio_id") portfolioId: String,
        @RequestBody request: SellAssetRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        ownUser(jwt, userId)

        val portfolio = ownPortfolio(jwt, portfolioId)

        positive(request.quantity, "Quantity")
        positive(request.pricePerUnit, "Price")

        val asset = assetFor(request.assetType, request.name)

        if (!checkPortfolioAssetQuantity(
                request.quantity,
                asset,
                portfolio
            )
        ) {
            return ResponseEntity.badRequest()
                .body("Insufficient asset quantity")
        }

        val proceeds = request.quantity * request.pricePerUnit

        record(
            asset,
            PortfolioActionEnum.SELL_REAL_ASSET,
            -request.quantity,
            proceeds,
            portfolio
        )

        val updated = portfolioRepository.save(
            portfolio.copy(
                cashBalance = portfolio.cashBalance + proceeds,
                updatedDate = LocalDateTime.now()
            )
        )

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "portfolio" to updated,
                "message" to "Successfully sold ${request.quantity} of ${request.name}"
            )
        )
    }

    // ============================================================
    // CASH TRANSACTIONS
    // ============================================================

    data class CashTransactionRequest(
        val amount: BigDecimal,
        val note: String? = null
    )

    data class CashTransactionResponse(
        val success: Boolean,
        val portfolio: Portfolio,
        val message: String,
        val transactionType: String,
        val amount: BigDecimal,
        val newBalance: BigDecimal
    )

    @PostMapping("/cash/deposit/{user_id}/{portfolio_id}")
    fun depositCash(
        @PathVariable("user_id") userId: String,
        @PathVariable("portfolio_id") portfolioId: String,
        @RequestBody request: CashTransactionRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        ownUser(jwt, userId)
        positive(request.amount, "Deposit amount")

        val portfolio = ownPortfolio(jwt, portfolioId)

        val newBalance = portfolio.cashBalance + request.amount

        val updated = portfolioRepository.save(
            portfolio.copy(
                cashBalance = newBalance,
                updatedDate = LocalDateTime.now()
            )
        )

        logger.info(
            "Simulated deposit: user={} portfolio={} amount={}",
            userId,
            portfolioId,
            request.amount
        )

        return ResponseEntity.ok(
            CashTransactionResponse(
                true,
                updated,
                "Successfully deposited ${request.amount}",
                "DEPOSIT",
                request.amount,
                newBalance
            )
        )
    }

    @PostMapping("/cash/withdraw/{user_id}/{portfolio_id}")
    fun withdrawCash(
        @PathVariable("user_id") userId: String,
        @PathVariable("portfolio_id") portfolioId: String,
        @RequestBody request: CashTransactionRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        ownUser(jwt, userId)
        positive(request.amount, "Withdrawal amount")

        val portfolio = ownPortfolio(jwt, portfolioId)

        if (portfolio.cashBalance < request.amount) {
            return ResponseEntity.badRequest()
                .body("Insufficient funds")
        }

        val newBalance = portfolio.cashBalance - request.amount

        val updated = portfolioRepository.save(
            portfolio.copy(
                cashBalance = newBalance,
                updatedDate = LocalDateTime.now()
            )
        )

        logger.info(
            "Simulated withdrawal: user={} portfolio={} amount={}",
            userId,
            portfolioId,
            request.amount
        )

        return ResponseEntity.ok(
            CashTransactionResponse(
                true,
                updated,
                "Successfully withdrew ${request.amount}",
                "WITHDRAW",
                request.amount,
                newBalance
            )
        )
    }

    // ============================================================
    // CASH BALANCE HISTORY
    // ============================================================

    @GetMapping("/cash/history/{user_id}/{portfolio_id}")
    fun getCashTransactionHistory(
        @PathVariable("user_id") userId: String,
        @PathVariable("portfolio_id") portfolioId: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        ownUser(jwt, userId)

        val portfolio = ownPortfolio(jwt, portfolioId)

        return ResponseEntity.ok(
            mapOf(
                "portfolioId" to portfolioId,
                "portfolioName" to portfolio.name,
                "currentBalance" to portfolio.cashBalance,
                "lastUpdated" to portfolio.updatedDate
            )
        )
    }
}
