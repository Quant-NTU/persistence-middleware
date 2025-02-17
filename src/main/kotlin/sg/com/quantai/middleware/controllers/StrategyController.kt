package sg.com.quantai.middleware.controllers

import jakarta.servlet.http.HttpServletRequest
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
import java.time.LocalDateTime

@RestController
@RequestMapping("/strategies")
class StrategyController(
    private val strategiesRepository: StrategyRepository,
    private val newStrategiesRepository: NewStrategyRepository,
    private val usersRepository: UserRepository
) {
    @Value("\${quantai.temp.s3.path}") var tempDirectory: String = "temp" //FIXME: Use value from properties instead of hardcoded solution
    @Value("\${quantai.persistence.s3.url}") var s3Url: String = "http://quant-ai-persistence-s3:8080" //FIXME: Use value from properties instead of hardcoded solution
    private var s3StrategyScriptsFolder: String = "strategy_scripts"
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
        @PathVariable("user_id") userId: String,
        request: HttpServletRequest
    ) : ResponseEntity<List<NewStrategy>>? {
        val user = usersRepository.findOneByUid(userId)
        val strategies = newStrategiesRepository.findByOwner(user)

        strategies.forEach{
            val filePath = it.path
            val strategyName = it.title
            val strategyUid = it.uid

            // Call to S3 service with additional logging parameters
            val response = s3WebClient()
                .get()
                .uri { builder ->
                    builder
                        .path("/") // Calls the root URL as per @GetMapping("")
                        .queryParam("path", filePath)
                        .queryParam("userId", userId)
                        .queryParam("strategyName", strategyName)
                        .build()
                }
                .header("X-User-IP", request.remoteAddr) // Passing the IP address in the header
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

    @PostMapping("/file/{user_id}", consumes = [MediaType.ALL_VALUE])
    fun saveFile(
        @RequestBody request: StrategyFileRequest,
        @PathVariable("user_id") userId: String
    ): ResponseEntity<NewStrategy> {
        // Log Post Request
        log.info("Received POST request with file payload: {}", request)

        // Retrieve user information
        val user = usersRepository.findOneByUid(userId)
        val uid = user.uid

        // Create temp dir, if it doesn't exist
        Files.createDirectories(tempStoragePath)

        // Check if strategy with this UID already exists
        var existingStrategy = newStrategiesRepository.findOneByUid(request.uid)
        val response: NewStrategy
        val filename: String

        if (existingStrategy != null) {
            // Update the existing strategy
            log.info("Updating existing strategy with uid: {}", request.uid)

            // Extract the existing filename
            filename = existingStrategy.path.substringAfterLast("/")

            // Define the paths for the original and backup files
            val originalFilePath = "$s3StrategyScriptsFolder/$uid/$filename"
            val backupFilePath = "$s3StrategyScriptsFolder/$uid/${filename}.bak"

            // Rename the original file to a .bak file in S3
            val renameResponse = s3WebClient()
                .put()
                .uri { builder ->
                    builder.path("/rename")
                        .queryParam("currentPath", originalFilePath)
                        .queryParam("newPath", backupFilePath)
                        .build()
                }
                .retrieve()
                .toEntity(String::class.java)
                .block()

            if (renameResponse?.statusCode != HttpStatus.OK) {
                log.error("Failed to back up the original file: $originalFilePath to $backupFilePath")
                return ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)
            }

            log.info("Backed up original file to: $backupFilePath")

            // Write new content to a temp file
            val tempFile = File("$tempStoragePath/$filename")
            tempFile.writeText(request.content)

            // Upload the new content to S3
            val path = "$s3StrategyScriptsFolder/$uid"
            val builder = MultipartBodyBuilder()
            builder.part("path", path)
            builder.part("file", tempFile.readBytes())
                .header("Content-Disposition", "form-data; name=file; filename=$filename")

            val uploadResponse = s3WebClient()
                .post()
                .uri("")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .toEntity(String::class.java)
                .block()

            // Check if the upload was successful
            if (uploadResponse?.statusCode != HttpStatus.OK) {
                log.error("Upload failed, restoring backup file")
                s3WebClient()
                    .put()
                    .uri { builder ->
                        builder.path("/rename")
                            .queryParam("currentPath", originalFilePath)
                            .queryParam("newPath", backupFilePath)
                            .build()
                    }
                    .retrieve()
                    .toEntity(String::class.java)
                    .block()
                return ResponseEntity(uploadResponse!!.statusCode)
            }

            log.info("Uploaded new file to: $path/$filename")

            // Delete temp file after successful upload
            tempFile.delete()

            // Delete the backup file as the new file is successfully uploaded
            val deleteBackupFileResponse = s3WebClient()
                .delete()
                .uri("?path=$backupFilePath")
                .retrieve()
                .toEntity(String::class.java)
                .block()

            if (deleteBackupFileResponse?.statusCode != HttpStatus.OK) {
                log.error("Failed to delete backup file: {}", backupFilePath)
            }

            // Update strategy details and save to the database
            existingStrategy.title = request.title
            existingStrategy.updatedDate = LocalDateTime.now()
            response = newStrategiesRepository.save(existingStrategy)

        } else {
            // Create a new strategy
            log.info("Creating a new strategy with uid: {}", request.uid)

            // Generate a new unique filename with the timestamp
            val filenameTimestamp = System.currentTimeMillis().toString()
            val filename = "$filenameTimestamp.py"
            val tempFile = File("$tempStoragePath/$filename")
            tempFile.writeText(request.content)

            // Prepare upload path
            val path = "$s3StrategyScriptsFolder/$uid"
            val builder = MultipartBodyBuilder()
            builder.part("path", path)
            builder.part("file", tempFile.readBytes())
                .header("Content-Disposition", "form-data; name=file; filename=$filename")

            val uploadResponse = s3WebClient()
                .post()
                .uri("")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .toEntity(String::class.java)
                .block()

            // Check if the upload was successful
            if (uploadResponse?.statusCode != HttpStatus.OK) {
                return ResponseEntity(uploadResponse!!.statusCode)
            }

            // Delete temp file after successful upload
            tempFile.delete()

            // Save the new strategy in the database
            response = newStrategiesRepository.save(
                NewStrategy(
                    title = request.title,
                    uid = filenameTimestamp,  // Use the original UID
                    path = "$path/$filename",  // Save the path of the file
                    owner = user,
                )
            )
        }

        // Set the content and return the response
        response.content = request.content
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

        // Check if there is a backup strategy named uid.bak 
        val backup: NewStrategy? = newStrategiesRepository.findOneByUid("$uid.bak")
        if (backup != null) {
            newStrategiesRepository.deleteByUid("$uid.bak")
        }
        
        if (strategy.owner.uid == user.uid) {
            val strategyPath = strategy.path
            val deleteResponse = s3WebClient()
                            .delete()
                            .uri("?path=$strategyPath")
                            .retrieve()
                            .toEntity(String::class.java)
                            .block()

            if (deleteResponse?.statusCode != HttpStatus.OK) {
                return ResponseEntity(deleteResponse!!.statusCode)
            }

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
    
    

    