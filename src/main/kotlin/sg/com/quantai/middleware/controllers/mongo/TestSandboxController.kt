
package sg.com.quantai.middleware.controllers.mongo

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import sg.com.quantai.middleware.data.mongo.PortfolioHistory
import sg.com.quantai.middleware.data.mongo.enums.PortfolioActionEnum
import sg.com.quantai.middleware.repositories.mongo.PortfolioHistoryRepository
import sg.com.quantai.middleware.repositories.mongo.PortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.StrategyRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import java.math.BigDecimal
import java.math.RoundingMode

@RestController
@RequestMapping("/test-sandbox")
class TestSandboxController(
    private val strategiesRepository: StrategyRepository,
    private val usersRepository: UserRepository,
    private val portfolioRepository: PortfolioRepository,
    private val portfolioHistoryRepository: PortfolioHistoryRepository
) {

    @Value("\${quantai.temp.s3.path}")
    var tempDirectory: String = "temp"

    @Value("\${quantai.persistence.s3.url}")
    var s3Url: String = "http://quant-ai-persistence-s3:8080"

    @Value("\${quantai.sandbox.url}")
    var sandboxUrl: String = "http://quant-ai-python-sandbox:5000"

    private val log =
        LoggerFactory.getLogger(TestSandboxController::class.java)

    private fun s3WebClient(): WebClient =
        WebClient.builder().baseUrl(s3Url).build()

    private fun sandboxWebClient(): WebClient =
        WebClient.builder().baseUrl(sandboxUrl).build()

    // ============================================================
    // PORTFOLIO HISTORY AGGREGATION
    // ============================================================

    fun aggregatePortfolioHistory(
        portfolioHistoryList: List<PortfolioHistory>
    ): List<Map<String, Any>> {
        return portfolioHistoryList
            .groupBy { it.asset.name }
            .map { (symbol, entries) ->

                val totalQuantity: BigDecimal = entries.sumOf { entry ->
                    when (entry.action) {
                        PortfolioActionEnum.SELL_REAL_ASSET ->
                            -entry.quantity.abs()

                        PortfolioActionEnum.REMOVE_MANUAL_ASSET ->
                            -entry.quantity.abs()

                        PortfolioActionEnum.ADD_MANUAL_ASSET,
                        PortfolioActionEnum.BUY_REAL_ASSET ->
                            entry.quantity

                        else -> BigDecimal.ZERO
                    }
                }

                val totalValue: BigDecimal = entries.sumOf { entry ->
                    when (entry.action) {
                        PortfolioActionEnum.SELL_REAL_ASSET,
                        PortfolioActionEnum.REMOVE_MANUAL_ASSET ->
                            -entry.value.abs()

                        PortfolioActionEnum.ADD_MANUAL_ASSET,
                        PortfolioActionEnum.BUY_REAL_ASSET ->
                            entry.value

                        else -> BigDecimal.ZERO
                    }
                }

                val purchasePrice =
                    if (totalQuantity.signum() == 0) {
                        BigDecimal.ZERO
                    } else {
                        totalValue.divide(
                            totalQuantity,
                            8,
                            RoundingMode.HALF_UP
                        )
                    }

                mapOf<String, Any>(
   		    "symbol" to (symbol ?: "UNKNOWN"),
    		    "quantity" to totalQuantity,
    		    "value" to totalValue,
   		    "purchasePrice" to purchasePrice
		)
            }
    }

    // ============================================================
    // PRELOAD CRYPTO DATA
    // ============================================================

    fun preloadCryptoData(
        webClient: WebClient = WebClient.create(),
        cryptoBaseUrl: String =
            "http://quant-ai-persistence-etl:10070/crypto",
        cryptoSymbols: List<String> = listOf("BTC", "ETH"),
        startDate: String? = null,
        endDate: String? = null,
        log: org.slf4j.Logger
    ) {
        try {
            cryptoSymbols.forEach { symbol ->
                val uri = StringBuilder(
                    "$cryptoBaseUrl/historical/store-by-date?symbol=$symbol&currency=USD"
                )

                if (startDate != null) {
                    uri.append("&startDate=$startDate")
                }

                if (endDate != null) {
                    uri.append("&endDate=$endDate")
                }

                val response = webClient.post()
                    .uri(uri.toString())
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .doOnError { e ->
                        log.error(
                            "Error preloading {}: {}",
                            symbol,
                            e.message
                        )
                    }
                    .block()

                log.info("Preloaded crypto {}: {}", symbol, response)
            }

            val response = webClient.post()
                .uri("$cryptoBaseUrl/transform")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

            log.info("Crypto transform completed: {}", response)

        } catch (e: Exception) {
            log.warn(
                "Failed to preload crypto data: {}",
                e.message
            )
        }
    }

    // ============================================================
    // PRELOAD STOCK DATA
    // ============================================================

    fun preloadStockData(
        webClient: WebClient = WebClient.create(),
        stockBaseUrl: String =
            "http://quant-ai-persistence-etl:10070/stock",
        stockSymbols: List<String> = listOf("AAPL", "MSFT"),
        startDate: String? = null,
        endDate: String? = null,
        log: org.slf4j.Logger
    ) {
        try {
            stockSymbols.forEach { symbol ->
                val uri = StringBuilder(
                    "$stockBaseUrl/historical/store-by-date?symbol=$symbol"
                )

                if (startDate != null) {
                    uri.append("&startDate=$startDate")
                }

                if (endDate != null) {
                    uri.append("&endDate=$endDate")
                }

                val response = webClient.post()
                    .uri(uri.toString())
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .doOnError { e ->
                        log.error(
                            "Error preloading {}: {}",
                            symbol,
                            e.message
                        )
                    }
                    .block()

                log.info("Preloaded stock {}: {}", symbol, response)
            }

            val response = webClient.post()
                .uri("$stockBaseUrl/transform")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

            log.info("Stock transform completed: {}", response)

        } catch (e: Exception) {
            log.warn(
                "Failed to preload stock data: {}",
                e.message
            )
        }
    }

    // ============================================================
    // PRELOAD FOREX DATA
    // ============================================================

    fun preloadForexData(
        webClient: WebClient = WebClient.create(),
        forexBaseUrl: String =
            "http://quant-ai-persistence-etl:10070/forex",
        forexPairs: List<String> = listOf("USD/GBP", "GBP/USD"),
        startDate: String? = null,
        endDate: String? = null,
        interval: String = "1day",
        log: org.slf4j.Logger
    ) {
        try {
            forexPairs.forEach { pair ->
                val uri = StringBuilder(
                    "$forexBaseUrl/historical/store-by-date" +
                        "?currencyPair=$pair&interval=$interval"
                )

                if (startDate != null) {
                    uri.append("&startDate=$startDate")
                }

                if (endDate != null) {
                    uri.append("&endDate=$endDate")
                }

                val response = webClient.post()
                    .uri(uri.toString())
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .doOnError { e ->
                        log.error(
                            "Error preloading forex {}: {}",
                            pair,
                            e.message
                        )
                    }
                    .block()

                log.info("Preloaded forex {}: {}", pair, response)
            }

            val response = webClient.post()
                .uri("$forexBaseUrl/transform")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

            log.info("Forex transform completed: {}", response)

        } catch (e: Exception) {
            log.warn(
                "Failed to preload forex data: {}",
                e.message
            )
        }
    }

    // ============================================================
    // EXECUTE STRATEGY IN PYTHON SANDBOX
    // ============================================================

    fun executeSandboxStrategy(
        webClient: WebClient,
        userId: String,
        strategyId: String,
        startDate: String?,
        endDate: String?,
        startingCash: Double?,
        transactionFeePercent: Double?,
        marginEnabled: Boolean?,
        initialMarginRate: Double?,
        maintenanceMarginRate: Double?,
        portfolioJson: Any,
        strategyCode: String?
    ): String? {
        return webClient
            .post()
            .uri { builder ->
                builder.path(
                    "/strategies/user/$userId/$strategyId/execute"
                )

                startDate?.let {
                    builder.queryParam("startDate", it)
                }
                endDate?.let {
                    builder.queryParam("endDate", it)
                }
                startingCash?.let {
                    builder.queryParam("startingCash", it)
                }
                transactionFeePercent?.let {
                    builder.queryParam(
                        "transaction_fee_percent",
                        it
                    )
                }
                marginEnabled?.let {
                    builder.queryParam("marginEnabled", it)
                }
                initialMarginRate?.let {
                    builder.queryParam("initialMarginRate", it)
                }
                maintenanceMarginRate?.let {
                    builder.queryParam("maintenanceMarginRate", it)
                }

                builder.build()
            }
            .bodyValue(
                mapOf(
                    "portfolio" to portfolioJson,
                    "strategyCode" to strategyCode
                )
            )
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
    }

    @PostMapping("/user/{user_id}/{strategy_id}/run")
    fun runStrategy(
        @PathVariable("user_id") userId: String,
        @PathVariable("strategy_id") strategyId: String,
        @RequestParam(required = false) portfolioUid: String? = null,
        @RequestParam(required = false) startDate: String? = null,
        @RequestParam(required = false) endDate: String? = null,
        @RequestParam(required = false) startingCash: Double? = null,
        @RequestParam(required = false) transactionFeePercent: Double? = null,
        @RequestParam(required = false) marginEnabled: Boolean? = null,
        @RequestParam(required = false) initialMarginRate: Double? = null,
        @RequestParam(required = false) maintenanceMarginRate: Double? = null,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {

        // 1. Verify authenticated user.
        if (jwt.subject != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Cannot execute strategies for another user")
        }

        val user = usersRepository.findOneByUid(jwt.subject)

        // 2. Verify strategy exists and belongs to user.
        val strategy = strategiesRepository.findOneByUid(strategyId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Strategy not found")

        if (strategy.owner.uid != jwt.subject) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Strategy not found")
        }

        // 3. Retrieve only a portfolio belonging to this user.
        val portfolio = if (!portfolioUid.isNullOrEmpty()) {
            portfolioRepository.findOneByUidAndOwner(
                portfolioUid,
                user
            )
        } else {
            portfolioRepository.findByOwnerAndMain(user, true)
        }

        if (portfolio == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Portfolio not found")
        }

        // 4. Preload market data.
        preloadCryptoData(
            webClient = WebClient.create(),
            cryptoSymbols = listOf("BTC", "ETH"),
            startDate = startDate,
            endDate = endDate,
            log = log
        )

        preloadStockData(
            webClient = WebClient.create(),
            stockSymbols = listOf("AAPL", "MSFT"),
            startDate = startDate,
            endDate = endDate,
            log = log
        )

        preloadForexData(
            webClient = WebClient.create(),
            forexPairs = listOf("USD/GBP", "GBP/USD"),
            startDate = startDate,
            endDate = endDate,
            log = log
        )

        // 5. Download the authenticated user's strategy code.
        val s3Response = s3WebClient()
            .get()
            .uri { builder ->
                builder.path("/")
                    .queryParam("path", strategy.path)
                    .queryParam("userId", jwt.subject)
                    .queryParam("strategyName", strategy.title)
                    .build()
            }
            .retrieve()
            .toEntity(String::class.java)
            .block()

        val strategyCode = s3Response?.body

        if (strategyCode.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("Unable to retrieve strategy code")
        }

        // 6. Prepare portfolio data.
        val portfolioHistory =
            portfolioHistoryRepository.findByPortfolio(portfolio)

        val aggregatedAssets =
            aggregatePortfolioHistory(portfolioHistory)

        val portfolioJson = mapOf(
            "uid" to portfolio.uid,
            "assets" to aggregatedAssets
        )

        // 7. Execute strategy.
        return try {
            val sandboxResponse = executeSandboxStrategy(
                sandboxWebClient(),
                jwt.subject,
                strategyId,
                startDate,
                endDate,
                startingCash,
                transactionFeePercent,
                marginEnabled,
                initialMarginRate,
                maintenanceMarginRate,
                portfolioJson,
                strategyCode
            )

            ResponseEntity.ok(sandboxResponse)

        } catch (e: Exception) {
            log.error("Sandbox execution failed", e)

            ResponseEntity.status(
                HttpStatus.BAD_GATEWAY
            ).body("Sandbox execution failed")
        }
    }
}
