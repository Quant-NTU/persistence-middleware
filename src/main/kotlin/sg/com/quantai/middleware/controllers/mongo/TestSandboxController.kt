package sg.com.quantai.middleware.controllers.mongo

import sg.com.quantai.middleware.repositories.mongo.StrategyRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.beans.factory.annotation.Value
import java.nio.file.Paths
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.bind.annotation.RequestParam

import sg.com.quantai.middleware.data.mongo.Portfolio
import sg.com.quantai.middleware.data.mongo.PortfolioHistory
import sg.com.quantai.middleware.repositories.mongo.PortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.PortfolioHistoryRepository
import sg.com.quantai.middleware.data.mongo.Asset
import sg.com.quantai.middleware.data.mongo.enums.PortfolioActionEnum
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
    @Value("\${quantai.temp.s3.path}") var tempDirectory: String = "temp" //FIXME: Use value from properties instead of hardcoded solution
    @Value("\${quantai.persistence.s3.url}") var s3Url: String = "http://quant-ai-persistence-s3:8080" //FIXME: Use value from properties instead of hardcoded solution
    @Value("\${quantai.sandbox.url}") var sandboxUrl: String = "http://quant-ai-python-sandbox:5000" //FIXME: Use value from properties instead of hardcoded solution

    private var s3StrategyScriptsFolder: String = "strategy_scripts"
    private val tempStoragePath = Paths.get(tempDirectory)
    private val log = LoggerFactory.getLogger(StrategyController::class.java)
    private val objectMapper = ObjectMapper()

    private fun s3WebClient() : WebClient {
        return WebClient.builder().baseUrl(s3Url).build()
    }

    private fun sandboxWebClient() : WebClient {
        return WebClient.builder().baseUrl(sandboxUrl).build()
    }

    fun aggregatePortfolioHistory(portfolioHistoryList: List<PortfolioHistory>): List<Map<String, Any>> {
        return portfolioHistoryList
            .groupBy { history -> history.asset.name ?: "UNKNOWN" }
            .map { (symbol, entries) ->
                val totalQuantity: BigDecimal = entries.sumOf { entry ->
                    when (entry.action) {
                        PortfolioActionEnum.SELL_REAL_ASSET,
                        PortfolioActionEnum.REMOVE_MANUAL_ASSET -> entry.quantity.negate()
                        else -> entry.quantity
                    }
                }
                val totalValue: BigDecimal = entries.sumOf { entry ->
                    when (entry.action) {
                        PortfolioActionEnum.SELL_REAL_ASSET,
                        PortfolioActionEnum.REMOVE_MANUAL_ASSET -> entry.value.negate()
                        else -> entry.value
                    }
                }
                // if no quantity, puchase price set to 0
                val purchasePrice: BigDecimal =
                    if (totalQuantity.signum() == 0) BigDecimal.ZERO
                    else totalValue.divide(totalQuantity, 8, RoundingMode.HALF_UP)
                
                mapOf(
                    "symbol" to symbol,
                    "quantity" to totalQuantity,
                    "value" to totalValue,
                    "purchasePrice" to purchasePrice
                )
            }
    }

    fun preloadCryptoData(
        webClient: WebClient = WebClient.create(),  // Can be mocked in tests
        cryptoBaseUrl: String = "http://quant-ai-persistence-etl:10070/crypto",
        cryptoSymbols: List<String> = listOf("BTC", "ETH"),
        startDate: String? = null,
        endDate: String? = null,
        log: org.slf4j.Logger
    ) {
        try {
            cryptoSymbols.forEach { symbol ->
                val uri = StringBuilder("$cryptoBaseUrl/historical/store-by-date?symbol=$symbol&currency=USD")
                if (startDate != null) uri.append("&startDate=$startDate")
                if (endDate != null) uri.append("&endDate=$endDate")

                val response = webClient.post()
                    .uri(uri.toString())
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .doOnError { e -> log.error("Error preloading $symbol: ${e.message}") }
                    .block()

                log.info("Preloaded data for $symbol: $response")
            }

            val transformResponse = webClient.post()
                .uri("$cryptoBaseUrl/transform")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

            log.info("Transform completed: $transformResponse")
            log.info("✅ Successfully preloaded crypto data for symbols: $cryptoSymbols")

        } catch (e: Exception) {
            log.warn("⚠️ Failed to preload crypto data before strategy run: ${e.message}")
        }
    }





    @PostMapping("/user/{user_id}/{strategy_id}/run")
    fun runStrategy(
        @PathVariable("user_id") userId: String,
        @PathVariable("strategy_id") strategyId: String,
        @RequestParam(required = false) portfolioUid: String? = null,
        @RequestParam(required = false) startDate: String? = null,
        @RequestParam(required = false) endDate: String? = null
    ): ResponseEntity<Any> {
        val user = usersRepository.findOneByUid(userId)
        val strategy = strategiesRepository.findOneByUid(strategyId)
        if (strategy == null) return ResponseEntity(HttpStatus.NOT_FOUND)

        // Retrieve portfolio
        val portfolio = if (!portfolioUid.isNullOrEmpty()) {
            portfolioRepository.findOneByUidAndOwner(portfolioUid, user)
        } else {
            portfolioRepository.findByOwnerAndMain(user, true)
        }
        if (portfolio == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Portfolio not found")

        preloadCryptoData(
            webClient = WebClient.create(),
            cryptoBaseUrl = "http://quant-ai-persistence-etl:10070/crypto",
            cryptoSymbols = listOf("BTC", "ETH"),  // currently static
            startDate = startDate,
            endDate = endDate,
            log = log
        )

        val strategyName = strategy.title
        val filePath = strategy.path
        val s3Response = s3WebClient()
            .get()
            .uri {
                it.path("/")
                    .queryParam("path", filePath)
                    .queryParam("userId", userId)
                    .queryParam("strategyName", strategyName)
                    .build()
            }
            .retrieve()
            .toEntity(String::class.java)
            .block()
        val strategyCode = s3Response!!.body

<<<<<<< HEAD
        val portfolio = portfolioRepository.findByOwnerAndMain(user, true)
        
        if (portfolio == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Main portfolio not found for user")
        }

=======
>>>>>>> 71681b8 (changed runStrategy to be able to accept portfolioUid and use it to select the portfolio)
        val portfolioHistory = portfolioHistoryRepository.findByPortfolio(portfolio)
        val aggregatedAssets = aggregatePortfolioHistory(portfolioHistory)

        val portfolioJson = mapOf(
            "uid" to portfolio.uid,
            "assets" to aggregatedAssets
        )

        try {
            val sandboxResponse = sandboxWebClient()
                .post()
                .uri("/strategies/user/${userId}/${strategyId}/execute?startDate=$startDate&endDate=$endDate") //?startDate=$startDate&endDate=$endDate
                .bodyValue(mapOf(
                    "portfolio" to portfolioJson,
                    "strategyCode" to strategyCode
                ))
                .exchangeToMono { response ->
                    if (!response.statusCode().is2xxSuccessful) {
                        response.bodyToMono(String::class.java)
                            .map { err ->
                                log.info("Detailed Sandbox error: $err")
                                val errMsg = objectMapper.readTree(err).path("detail")
                                throw Exception(errMsg.asText())
                            }
                    } else {
                        response.bodyToMono(String::class.java)
                    }
                }
                .block()

            return ResponseEntity.ok(sandboxResponse)
        } catch (e: Exception) {
            return ResponseEntity.status(500).body("Error: ${e.message}")
        }
    }

}