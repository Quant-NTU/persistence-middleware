
package sg.com.quantai.middleware.controllers.mongo

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import sg.com.quantai.middleware.data.mongo.Strategy
import sg.com.quantai.middleware.repositories.mongo.StrategyRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import sg.com.quantai.middleware.requests.StrategyFileRequest
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.UUID

@RestController
@RequestMapping("/strategies")
class StrategyController(
    private val strategiesRepository: StrategyRepository,
    private val usersRepository: UserRepository
) {

    @Value("\${quantai.temp.s3.path}")
    var tempDirectory: String = "temp"

    @Value("\${quantai.persistence.s3.url}")
    var s3Url: String = "http://quant-ai-persistence-s3:8080"

    private val s3StrategyScriptsFolder = "strategy_scripts"
    private val log = LoggerFactory.getLogger(StrategyController::class.java)

    private fun s3WebClient(): WebClient =
        WebClient.builder().baseUrl(s3Url).build()

    // ============================================================
    // AUTHORIZATION HELPERS
    // ============================================================

    private fun requireOwnUser(jwt: Jwt, userId: String) {
        if (jwt.subject != userId) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You cannot access another user's strategies"
            )
        }
    }

    private fun requireOwnStrategy(
        jwt: Jwt,
        strategyId: String
    ): Strategy {
        val strategy = strategiesRepository.findOneByUid(strategyId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Strategy not found"
            )

        if (strategy.owner.uid != jwt.subject) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Strategy not found"
            )
        }

        return strategy
    }

    // ============================================================
    // S3 HELPERS
    // ============================================================

    private fun uploadStrategy(
        userId: String,
        filename: String,
        content: String
    ): Boolean {
        val tempPath = Paths.get(tempDirectory)
        Files.createDirectories(tempPath)

        val tempFile = Files.createTempFile(
            tempPath,
            "strategy-",
            ".py"
        ).toFile()

        try {
            tempFile.writeText(content)

            val builder = MultipartBodyBuilder()
            builder.part(
                "path",
                "$s3StrategyScriptsFolder/$userId"
            )
            builder.part("file", tempFile.readBytes())
                .header(
                    "Content-Disposition",
                    "form-data; name=file; filename=$filename"
                )

            val response = s3WebClient()
                .post()
                .uri("")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .toEntity(String::class.java)
                .block()

            return response?.statusCode == HttpStatus.OK

        } finally {
            tempFile.delete()
        }
    }

    private fun renameStrategyFile(
        currentPath: String,
        newPath: String
    ): Boolean {
        val response = s3WebClient()
            .put()
            .uri { builder ->
                builder.path("/rename")
                    .queryParam("currentPath", currentPath)
                    .queryParam("newPath", newPath)
                    .build()
            }
            .retrieve()
            .toEntity(String::class.java)
            .block()

        return response?.statusCode == HttpStatus.OK
    }

    private fun deleteStrategyFile(path: String): Boolean {
        val response = s3WebClient()
            .delete()
            .uri { builder ->
                builder.path("/")
                    .queryParam("path", path)
                    .build()
            }
            .retrieve()
            .toEntity(String::class.java)
            .block()

        return response?.statusCode == HttpStatus.OK
    }

    // ============================================================
    // VIEW STRATEGIES
    // ============================================================

    @GetMapping("")
    fun getAllStrategies(
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<List<Strategy>> {
        val user = usersRepository.findOneByUid(jwt.subject)

        return ResponseEntity.ok(
            strategiesRepository.findByOwner(user)
        )
    }

    @GetMapping("/user/{user_id}")
    fun getAllStrategiesFromUser(
        @PathVariable("user_id") userId: String,
        request: HttpServletRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<List<Strategy>> {
        requireOwnUser(jwt, userId)

        val user = usersRepository.findOneByUid(jwt.subject)
        val strategies = strategiesRepository.findByOwner(user)

        strategies.forEach { strategy ->
            // The database query is scoped to the authenticated owner.
            val response = s3WebClient()
                .get()
                .uri { builder ->
                    builder.path("/")
                        .queryParam("path", strategy.path)
                        .queryParam("userId", jwt.subject)
                        .queryParam("strategyName", strategy.title)
                        .build()
                }
                .header("X-User-IP", request.remoteAddr)
                .retrieve()
                .toEntity(String::class.java)
                .block()

            strategy.content = response?.body
        }

        return ResponseEntity.ok(strategies)
    }

    @GetMapping("/user/{user_id}/{strategy_id}")
    fun getOneStrategyFromUser(
        @PathVariable("user_id") userId: String,
        @PathVariable("strategy_id") strategyId: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Strategy> {
        requireOwnUser(jwt, userId)

        return ResponseEntity.ok(
            requireOwnStrategy(jwt, strategyId)
        )
    }

    // ============================================================
    // CREATE OR UPDATE STRATEGY
    // ============================================================

    @PostMapping("/file/{user_id}", consumes = [MediaType.ALL_VALUE])
    fun saveFile(
        @RequestBody request: StrategyFileRequest,
        @PathVariable("user_id") userId: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Strategy> {
        requireOwnUser(jwt, userId)

        val user = usersRepository.findOneByUid(jwt.subject)

        // Do not log the request body: it contains strategy source code.
        log.info("Strategy save requested by user {}", jwt.subject)

        val existingStrategy =
            strategiesRepository.findOneByUid(request.uid)

        val response: Strategy

        if (existingStrategy != null) {
            // Never allow overwriting another user's strategy.
            if (existingStrategy.owner.uid != jwt.subject) {
                throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Strategy not found"
                )
            }

            val originalPath = existingStrategy.path
            val filename = originalPath.substringAfterLast("/")
            val backupPath = "$originalPath.bak"

            if (!renameStrategyFile(originalPath, backupPath)) {
                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to back up existing strategy"
                )
            }

            try {
                if (!uploadStrategy(userId, filename, request.content)) {
                    throw IllegalStateException(
                        "Strategy upload failed"
                    )
                }
            } catch (e: Exception) {
                log.error(
                    "Strategy upload failed for user {}",
                    jwt.subject,
                    e
                )

                try {
                    renameStrategyFile(backupPath, originalPath)
                } catch (restoreError: Exception) {
                    log.error(
                        "Failed to restore strategy backup",
                        restoreError
                    )
                }

                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to update strategy file"
                )
            }

            // Backup cleanup failure should not prevent a successful save.
            try {
                deleteStrategyFile(backupPath)
            } catch (e: Exception) {
                log.warn(
                    "Failed to delete strategy backup: {}",
                    e.message
                )
            }

            existingStrategy.title = request.title
            existingStrategy.updatedDate = LocalDateTime.now()

            response = strategiesRepository.save(existingStrategy)

        } else {
            val strategyUid = UUID.randomUUID().toString()
            val filename = "$strategyUid.py"

            if (!uploadStrategy(userId, filename, request.content)) {
                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to upload strategy file"
                )
            }

            response = strategiesRepository.save(
                Strategy(
                    title = request.title,
                    uid = strategyUid,
                    path = "$s3StrategyScriptsFolder/$userId/$filename",
                    owner = user
                )
            )
        }

        response.content = request.content
        return ResponseEntity.ok(response)
    }

    // ============================================================
    // DELETE STRATEGY
    // ============================================================

    @DeleteMapping("/user/{user_id}/{uid}")
    fun deleteStrategyFromUser(
        @PathVariable("user_id") userId: String,
        @PathVariable("uid") uid: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        requireOwnUser(jwt, userId)

        val strategy = requireOwnStrategy(jwt, uid)

        if (!deleteStrategyFile(strategy.path)) {
            return ResponseEntity.status(
                HttpStatus.BAD_GATEWAY
            ).body("Failed to delete strategy file")
        }

        strategiesRepository.deleteByUid(strategy.uid)

        return ResponseEntity.ok(
            "Deleted strategy ${strategy.uid}"
        )
    }
}
