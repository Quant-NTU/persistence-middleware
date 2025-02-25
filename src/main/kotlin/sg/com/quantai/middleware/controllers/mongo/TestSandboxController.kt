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

@RestController
@RequestMapping("/test-sandbox")
class TestSandboxController(
    private val strategiesRepository: StrategyRepository,
    private val usersRepository: UserRepository
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

    @PostMapping("/user/{user_id}/{strategy_id}/run")
    fun runStrategy(
        @PathVariable("user_id") userId: String,
        @PathVariable("strategy_id") strategyId: String
    ): ResponseEntity<Any> {
        val user = usersRepository.findOneByUid(userId)
        val strategy = strategiesRepository.findOneByUid(strategyId)
        if (strategy == null) {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }

        // Retrieve content of strategy script
        val strategyName = strategy.title
        val filePath = strategy.path
        val s3Response = s3WebClient()
                            .get()
                            .uri { builder ->
                                builder
                                    .path("/")
                                    .queryParam("path", filePath)
                                    .queryParam("userId", userId)
                                    .queryParam("strategyName", strategyName)
                                    .build()
                            }
                            .retrieve()
                            .toEntity(String::class.java)
                            .block()
        val strategyCode = s3Response!!.body

        // Retrieve portfolio
        // TODO: Hardcoded portfolioId=1
        val portfolio = mapOf(
            "uid" to "1",
            "assets" to listOf(
                mapOf(
                    "symbol" to "BTC",
                    "quantity" to 1.0,
                    "purchasePrice" to 96000
                ),
                mapOf(
                    "symbol" to "ETH",
                    "quantity" to 1.0,
                    "purchasePrice" to 4000
                )
            )
        )

        // Call Python Sandbox API to execute the strategy
        try {
            val sandboxResponse = sandboxWebClient()
                .post()
                .uri("/strategies/user/${userId}/${strategyId}/execute")
                .bodyValue(mapOf(
                    "portfolio" to portfolio,
                    "strategyCode" to strategyCode
                ))
                .exchangeToMono { response ->
                    // Handle Python's error response
                    if (!response.statusCode().is2xxSuccessful) {
                        response.bodyToMono(String::class.java)
                            .map { err ->
                                log.info("Detailed Sandbox error: ${err}")
                                val errMsg = objectMapper.readTree(err).path("detail")
                                throw Exception(errMsg.asText())
                            }
                    } else {
                        response.bodyToMono(String::class.java)
                    }
                }
                .block()

            // TODO: Process 'sandboxResponse'
            return ResponseEntity.ok(sandboxResponse)
        } catch (e: Exception) {
            return ResponseEntity.status(500).body("Error: ${e.message}")
        }
    }

}