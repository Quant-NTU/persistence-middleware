package sg.com.quantai.middleware.controllers.mongo

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

@RestController
@RequestMapping("/strategies")
class StrategyController(
    private val newStrategiesRepository: StrategyRepository,
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
        val strategies = newStrategiesRepository.findAll()
        return ResponseEntity.ok(strategies)
    }

    // Retrieve all strategies from a user
    @GetMapping("/user/{user_id}")
    fun getAllStrategiesFromUser(
        @PathVariable("user_id") userId: String
    ) : ResponseEntity<List<Strategy>>? {
        val user = usersRepository.findOneByUid(userId)
        val strategies = newStrategiesRepository.findByOwner(user)

        strategies.forEach{
            val response = s3WebClient()
                                .get()
                                .uri("?path=${it.path}&userId=${it.owner.uid}&strategyName=${it.title}")
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
        val strategy = newStrategiesRepository.findOneByUid(strategy_id)
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
                                .uri("")
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
        val response: Strategy = newStrategiesRepository.save(
            Strategy(
                title = request.title,
                uid = filenameTimestamp,
                path = scriptPath,
                owner = user,
            )
        )

        response.content = request.content

        return ResponseEntity.ok(response)
    }

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
        val backup: Strategy? = newStrategiesRepository.findOneByUid("$uid.bak")
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

}
    
    

    