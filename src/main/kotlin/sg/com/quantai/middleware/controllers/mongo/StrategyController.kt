package sg.com.quantai.middleware.controllers.mongo

import jakarta.servlet.http.HttpServletRequest
import sg.com.quantai.middleware.data.mongo.Strategy
import sg.com.quantai.middleware.requests.StrategyFileRequest
import sg.com.quantai.middleware.repositories.mongo.StrategyRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import java.io.File
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
import java.time.LocalDateTime

@RestController
@RequestMapping("/strategies")
class StrategyController(
    private val strategiesRepository: StrategyRepository,
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
    ) : ResponseEntity<List<Strategy>>? {
        val user = usersRepository.findOneByUid(userId)
        val strategies = strategiesRepository.findByOwner(user)

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

    @PostMapping("/file/{user_id}", consumes=[MediaType.ALL_VALUE])
    fun saveFile(
        @RequestBody request: StrategyFileRequest,
        @PathVariable("user_id") userId: String
    ) : ResponseEntity<Strategy> {
        // Log Post Request
        log.info("Received POST request with file payload: {}", request)

        // Retrieve user information
        val user = usersRepository.findOneByUid(userId)
        val uid = user.uid

        // Create temp dir, if it doesn't exist
        Files.createDirectories(tempStoragePath)

        // Check if strategy with this UID already exists
        var existingStrategy = strategiesRepository.findOneByUid(request.uid)
        val response: Strategy
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
            response = strategiesRepository.save(existingStrategy)

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
            response = strategiesRepository.save(
                Strategy(
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
        val strategy = strategiesRepository.findOneByUid(uid)
        if (strategy == null) {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }

        // Check if there is a backup strategy named uid.bak 
        val backup: Strategy? = strategiesRepository.findOneByUid("$uid.bak")
        if (backup != null) {
            strategiesRepository.deleteByUid("$uid.bak")
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

            strategiesRepository.deleteByUid(strategy.uid)
            
            return ResponseEntity.ok().body("Deleted strategy ${uid}")
        }
        return ResponseEntity.noContent().build()
    }

}
    
    

    