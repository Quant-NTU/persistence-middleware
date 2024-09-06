package monolith
import org.slf4j.LoggerFactory
import monolith.data.*
import monolith.request.StrategyRequest
import monolith.request.StrategyFileRequest
import monolith.request.TransactionRequest
import org.bson.types.ObjectId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.CrossOrigin

import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import monolith.data.Transaction
import java.math.BigDecimal

import org.springframework.context.annotation.Configuration
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.client.RestTemplate
import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.io.FileWriter

@RestController
@RequestMapping("/strategies")
class StrategyController(
    private val strategiesRepository: StrategyRepository,
    private val usersRepository: UserRepository,
    private val cryptoRepository: CryptoRepository,
    private val stockRepository: StockRepository,
) {
    // @Value("\${quantai.temp.s3.path}") lateinit var tempDirectory: String
    // @Value("\${quantai.persistence.s3.url}") lateinit var s3_url: String
    private var tempDirectory: String = "temp"
    private var s3_url: String = "http://quant-ai-persistence-s3:8080/"
    private val tempStoragePath = Paths.get(tempDirectory)
    private var webClient: WebClient = WebClient.create()
    private val log = LoggerFactory.getLogger(StrategyController::class.java)

    // Retrieve all strategies
    @GetMapping("")
    fun getAllStrategies(): ResponseEntity<List<Strategy>> {
        val strategies = strategiesRepository.findAll()
        return ResponseEntity.ok(strategies)
    }

    // Retrieve all strategies from a user
    @GetMapping("/user/{user_id}")
    fun getAllStrategiesFromUser(
        @PathVariable("user_id") user_id: String
    ) : ResponseEntity<List<Strategy>> {
        val user = usersRepository.findOneByUid(user_id)
        val strategies = strategiesRepository.findByOwner(user)
        return ResponseEntity.ok(strategies)
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
        @PathVariable("user_id") user_id: String
    ) : ResponseEntity<String> {
        log.info("Received POST quest with file payload: {}", request)
        Files.createDirectories(tempStoragePath)
        val user = usersRepository.findOneByUid(user_id)?.uid

        // save
        val filename = "" + System.currentTimeMillis() + ".py"
        val file = File("$tempStoragePath/$filename")
        file.writeText(request.script)

        val builder = MultipartBodyBuilder()
        builder.part("path", "/strategy_scripts/$user")
        builder.part("file", File("$tempStoragePath/$filename").readBytes())
               .header("Content-Disposition", "form-data; name=file; filename=$filename")
        val bodyValues = builder.build()

        val response =
                webClient!!
                        .post()
                        .uri(s3_url + "/upload")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(BodyInserters.fromMultipartData(bodyValues))
                        .retrieve()
                        .toEntity(String::class.java)
                        .block()

        file.delete()
        return response!!
    }

    // // Create/Update a strategy from a user
    // @PostMapping("/user/{user_id}", consumes=[MediaType.ALL_VALUE])
    // fun saveOrUpdateStrategyFromUser(
    //     @RequestBody request: StrategyRequest,
    //     @PathVariable("user_id") user_id: String
    // ) : ResponseEntity<Strategy> {
    //     log.info("Received POST request with payload: {}", request);
    //     val user = usersRepository.findOneByUid(user_id)
    //     var response: Strategy
    //     if (request.uid == null) { //FIXME: This is always false
    //         // Create
    //         response = strategiesRepository.save(
    //             Strategy(
    //                 title = request.title,
    //                 script = request.script,
    //                 interval = request.interval,
    //                 status = request.status,
    //                 owner = user
    //             )
    //         )
    //     } else {
    //         // Update
    //         val strategy = strategiesRepository.findOneByUid(request.uid)
    //         if (strategy == null) {
    //             return ResponseEntity(HttpStatus.NOT_FOUND)
    //         }
    //         response = strategiesRepository.save(
    //             Strategy(
    //                 title = request.title,
    //                 script = request.script,
    //                 interval = request.interval,
    //                 status = request.status,
    //                 transactionCount = strategy.transactionCount,
    //                 owner = user,
    //                 createdDate = strategy.createdDate,
    //                 updatedDate = LocalDateTime.now(),
    //                 uid = request.uid,
    //                 _id = strategy._id
    //             )
    //         )
    //     }
    //     return ResponseEntity.ok(response)
    // }

    // Delete a strategy from a user (Could be "delete a strategy, but we will put the user as a security measure")
    @DeleteMapping("/user/{user_id}/{uid}")
    fun deleteStrategyFromUser(
        @RequestBody request: StrategyRequest,
        @PathVariable("user_id") user_id: String,
        @PathVariable("uid") uid: String
    ) : ResponseEntity<Any> {
        val user = usersRepository.findOneByUid(user_id)
        val strategy = strategiesRepository.findOneByUid(uid)
        if (strategy == null) {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
        if (strategy.owner.uid == user.uid) {
            strategiesRepository.deleteByUid(strategy.uid)
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
    
    

    