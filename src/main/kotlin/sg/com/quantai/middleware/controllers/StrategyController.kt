package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.Crypto
import sg.com.quantai.middleware.data.NewStrategy
import sg.com.quantai.middleware.data.Strategy
import sg.com.quantai.middleware.data.Stock
import sg.com.quantai.middleware.data.Transaction
import sg.com.quantai.middleware.data.enums.StrategyStatus
import sg.com.quantai.middleware.requests.StrategyRequest
import sg.com.quantai.middleware.requests.StrategyFileRequest
import sg.com.quantai.middleware.requests.TransactionRequest
import sg.com.quantai.middleware.repositories.NewStrategyRepository
import sg.com.quantai.middleware.repositories.UserRepository
import sg.com.quantai.middleware.repositories.StrategyRepository
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/strategies")
class StrategyController(
    private val strategiesRepository: StrategyRepository,
    private val newStrategiesRepository: NewStrategyRepository,
    private val usersRepository: UserRepository
) {
    @Value("\${quantai.temp.s3.path}") var tempDirectory: String = "temp" //FIXME: Use value from properties instead of hardcoded solution
    @Value("\${quantai.persistence.s3.url}") var s3Url: String = "http://quant-ai-persistence-s3:8080" //FIXME: Use value from properties instead of hardcoded solution
    private var s3StrategyScriptsFolder: String = "/strategy_scripts"
    private val tempStoragePath = Paths.get(tempDirectory)
    private val log = LoggerFactory.getLogger(StrategyController::class.java)

    private fun s3WebClient() : WebClient {
        return WebClient.builder().baseUrl(s3Url).build()
    }

    // Retrieve all strategies
    @GetMapping("")
    fun getAllStrategies(): ResponseEntity<List<Strategy>> {
        val strategies = strategiesRepository.findAll()
        return ResponseEntity.ok(strategies)
    }

    // Retrieve all strategies from a user
    @GetMapping("/user/{user_id}")
    fun getAllStrategiesFromUser(
        @PathVariable("user_id") userId: String
    ) : ResponseEntity<List<NewStrategy>>? {
        val user = usersRepository.findOneByUid(userId)
        val strategies = newStrategiesRepository.findByOwner(user)

        strategies.forEach{
            val filePath = it.path
            val response = s3WebClient()
                                .get()
                                .uri("/read?path=$filePath")
                                .retrieve()
                                .toEntity(String::class.java)
                                .block()

            it.content = response!!.body
        }

        return ResponseEntity(strategies, HttpStatus.OK)
    }

    @GetMapping("/user/{user_id}/{strategy_id}")
    fun getOneStrategyFromUser(
        @PathVariable("user_id") user_id: String,
        @PathVariable("strategy_id") strategy_id: String
    ) : ResponseEntity<Strategy> {
        // val user = usersRepository.findOneByUid(user_id)
        val strategy = strategiesRepository.findOneByUid(strategy_id)
        return ResponseEntity.ok(strategy)
        // if (user == strategy.owner) return ResponseEntity.ok(strategy)
        // TODO: Return error if user and strategy doesn't match
    }

    @GetMapping("/count")
    fun countStrategies(): ResponseEntity<Map<String, Long>> {
        val activeCount = strategiesRepository.countByStatus(StrategyStatus.ACTIVE)
        val inactiveCount = strategiesRepository.countByStatus(StrategyStatus.INACTIVE)
        val testingCount = strategiesRepository.countByStatus(StrategyStatus.TESTING)

        val countMap = mapOf(
            "activeStrategies" to activeCount,
            "inactiveStrategies" to inactiveCount,
            "testingStrategies" to testingCount,
        )

        return ResponseEntity.ok(countMap)
    }

    @PostMapping("/file/{user_id}", consumes=[MediaType.ALL_VALUE])
    fun saveFile(
        @RequestBody request: StrategyFileRequest,
        @PathVariable("user_id") userId: String
    ) : ResponseEntity<NewStrategy> {
        // Log Post Request
        log.info("Received POST quest with file payload: {}", request)

        // Retrieve user information
        val user = usersRepository.findOneByUid(userId)
        val uid = user.uid

        // Create temp dir, if it doesn't exist
        Files.createDirectories(tempStoragePath)

        // Prepare temp file
        val filenameTimestamp = "" + System.currentTimeMillis()
        val filename = "$filenameTimestamp.py"
        val file = File("$tempStoragePath/$filename")
        file.writeText(request.content)

        // Prepare upload path
        val path = "$s3StrategyScriptsFolder/$uid"

        // Build HTTP Request Body
        val builder = MultipartBodyBuilder()
        builder.part("path", path)
        builder.part("file", File("$tempStoragePath/$filename").readBytes())
               .header("Content-Disposition", "form-data; name=file; filename=$filename")

        // Send HTTP Post Request
        val uploadResponse = s3WebClient()
                                .post()
                                .uri("/upload")
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                                .body(BodyInserters.fromMultipartData(builder.build()))
                                .retrieve()
                                .toEntity(String::class.java)
                                .block()

        // Delete temp file
        file.delete()

        // Check if upload is successful
        if (uploadResponse?.statusCode != HttpStatus.OK) {
            return ResponseEntity(uploadResponse!!.statusCode)
        }

        // Save file path information in the database
        val scriptPath = "$path/$filename"
        val response: NewStrategy = newStrategiesRepository.save(
            NewStrategy(
                title = request.title,
                uid = filenameTimestamp,
                path = scriptPath,
                owner = user,
            )
        )

        return ResponseEntity.ok(response)
    }

    // Delete a strategy from a user (Could be "delete a strategy, but we will put the user as a security measure")
    @DeleteMapping("/user/{user_id}/{uid}")
    fun deleteStrategyFromUser(
        @PathVariable("user_id") user_id: String,
        @PathVariable("uid") uid: String
    ) : ResponseEntity<Any> {
        val user = usersRepository.findOneByUid(user_id)
        val strategy = newStrategiesRepository.findOneByUid(uid)
        if (strategy == null) {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
        if (strategy.owner.uid == user.uid) {
            newStrategiesRepository.deleteByUid(strategy.uid)
            
            return ResponseEntity.ok().body("Deleted strategy ${uid}")
        }
        return ResponseEntity.noContent().build()
    }


    @PostMapping("/user/{user_id}/{strategy_id}", consumes = ["application/json"])
    fun updateStrategyTransactions(
        @PathVariable user_id: String,
        @PathVariable strategy_id: String,
        @RequestBody transactionRequests: List<TransactionRequest>
    ): ResponseEntity<*> {

        val user = usersRepository.findOneByUid(user_id) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found") //FIXME: Elvis operator always returns the left operand of non-nullabel type User
        val strategy = strategiesRepository.findOneByUid(strategy_id) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Strategy not found") //FIXME: Elvis operator always returns the left operand of non-nullabel type User
        log.info("Updating strategy with transactions: $transactionRequests")
        val transactions = transactionRequests.mapNotNull { request ->
            // val crypto: Crypto? =  if (request.asset_type == "crypto") cryptoRepository.findOneBySymbol(symbol) else null
            val crypto: Crypto? =  request.crypto

            val stock: Stock? = request.stock

            val maxBuyPrice = when {
                request.maxBuyPrice != null && request.maxBuyPrice != "INF" -> BigDecimal(request.maxBuyPrice)
                request.maxBuyPrice == "INF" -> BigDecimal.valueOf(Long.MAX_VALUE) // Example logic for "INF"
                else -> null
            }

            val minSellPrice = when {
                request.minSellPrice != null && request.minSellPrice != "0.00" -> BigDecimal(request.minSellPrice)
                request.minSellPrice == "0.00" -> BigDecimal.ZERO
                else -> null
            }

            Transaction(
                crypto = crypto,
                stock = stock,
                quantity = request.quantity,
                price = request.price,
                type = request.type,
                strategy = strategy,
                strategyId = strategy?.uid, // FIXME: Unnecessary safe call on a non-null receiver of type Strategy
                owner = user,
                maxBuyPrice = maxBuyPrice,
                minSellPrice = minSellPrice,
                status = request.status
            )
        }
        log.info("Updating strategy with transactions: $transactions")
        strategy.transactions.clear() // Clear existing transactions if you need to replace them
        // Assume Strategy class has a mutable list to add transactions
        strategy.transactions.addAll(transactions)
        strategiesRepository.save(strategy)

        return ResponseEntity.ok().body("Transactions updated for strategy ${strategy_id}")
    }
}
    
    

    