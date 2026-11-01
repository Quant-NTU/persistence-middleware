
package sg.com.quantai.middleware.controllers.mongo

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.data.mongo.Pipeline
import sg.com.quantai.middleware.data.mongo.Strategy
import sg.com.quantai.middleware.repositories.mongo.PipelineRepository
import sg.com.quantai.middleware.repositories.mongo.StrategyRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import sg.com.quantai.middleware.requests.PipelineRequest
import java.time.LocalDateTime

@RestController
@RequestMapping("/pipelines")
class PipelineController(
    private val pipelineRepository: PipelineRepository,
    private val userRepository: UserRepository,
    private val newStrategiesRepository: StrategyRepository
) {

    private val log = LoggerFactory.getLogger(PipelineController::class.java)

    // ============================================================
    // OWNERSHIP HELPER
    // ============================================================

    private fun isOwnUser(
        request: HttpServletRequest,
        userId: String
    ): Boolean {
        val authenticatedUid =
            request.getAttribute("authenticatedUid") as? String

        return authenticatedUid != null &&
            authenticatedUid == userId
    }

    // ============================================================
    // GET ALL PIPELINES FOR AUTHENTICATED USER
    // ============================================================

    @GetMapping("")
    fun getAllPipelines(
        request: HttpServletRequest
    ): ResponseEntity<List<Pipeline>> {

        val authenticatedUid =
            request.getAttribute("authenticatedUid") as? String
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val user = userRepository.findOneByUid(authenticatedUid)

        val pipelines = pipelineRepository.findByOwner(user)

        return ResponseEntity.ok(pipelines)
    }

    // ============================================================
    // GET PIPELINES BELONGING TO A SPECIFIC USER
    // ============================================================

    @GetMapping("/user/{user_id}")
    fun getUserPipelines(
        @PathVariable("user_id") userId: String,
        request: HttpServletRequest
    ): ResponseEntity<List<Pipeline>> {

        if (!isOwnUser(request, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val user = userRepository.findOneByUid(userId)

        val pipelines = pipelineRepository.findByOwner(user)

        return ResponseEntity.ok(pipelines)
    }

    // ============================================================
    // GET ONE PIPELINE
    // ============================================================

    @GetMapping("/user/{user_id}/{pipeline_id}")
    fun getOnePipelineFromUser(
        @PathVariable("user_id") userId: String,
        @PathVariable("pipeline_id") pipelineId: String,
        request: HttpServletRequest
    ): ResponseEntity<Pipeline> {

        if (!isOwnUser(request, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pipeline = pipelineRepository.findOneByUid(pipelineId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        if (pipeline.owner.uid != userId) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        return ResponseEntity.ok(pipeline)
    }

    // ============================================================
    // CREATE PIPELINE
    // ============================================================

    @PostMapping("/{user_id}")
    fun createPipeline(
        @PathVariable("user_id") userId: String,
        @RequestBody request: PipelineRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Pipeline> {

        if (!isOwnUser(httpRequest, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val user = userRepository.findOneByUid(userId)

        var strategies = emptyList<Strategy>()

        if (request.strategies_id.isNotBlank()) {

            val regex = """strategyId"\s*:\s*"([^"]+)""".toRegex()

            val strategyIds = regex.findAll(request.strategies_id)
                .map { it.groupValues[1] }
                .toList()

            strategies = strategyIds.mapNotNull { strategyId ->

                val strategy =
                    newStrategiesRepository.findOneByUid(strategyId)

                if (strategy != null && strategy.owner?.uid == user.uid) {
                    strategy
                } else if (strategy?.owner?.uid != user.uid) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
                } else {
                    null
                }
            }
        }

        val pipeline = pipelineRepository.save(
            Pipeline(
                title = request.title,
                owner = user,
                description = request.description,
                strategies = strategies,
                createdDate = LocalDateTime.now()
            )
        )

        log.info("Pipeline created: {} by user {}", pipeline.uid, userId)

        return ResponseEntity.status(HttpStatus.CREATED).body(pipeline)
    }

    // ============================================================
    // DELETE PIPELINE
    // ============================================================

    @DeleteMapping("/user/{user_id}/{pipeline_id}")
    fun deletePipelineFromUser(
        @PathVariable("user_id") userId: String,
        @PathVariable("pipeline_id") pipelineId: String,
        request: HttpServletRequest
    ): ResponseEntity<Any> {

        if (!isOwnUser(request, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pipeline = pipelineRepository.findOneByUid(pipelineId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        if (pipeline.owner.uid != userId) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        pipelineRepository.deleteByUid(pipeline.uid)

        log.info("Pipeline deleted: {} by user {}", pipelineId, userId)

        return ResponseEntity.ok("Deleted pipeline $pipelineId")
    }

    // ============================================================
    // UPDATE PIPELINE
    // ============================================================

    @PutMapping("/user/{user_id}/{pipeline_id}")
    fun updatePipeline(
        @PathVariable("user_id") userId: String,
        @PathVariable("pipeline_id") pipelineId: String,
        @RequestBody request: PipelineRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Pipeline> {

        if (!isOwnUser(httpRequest, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val user = userRepository.findOneByUid(userId)

        val pipeline = pipelineRepository.findOneByUid(pipelineId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        if (pipeline.owner.uid != userId) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        var strategies = pipeline.strategies

        if (request.strategies_id.isNotBlank()) {

            val regex = """strategyId"\s*:\s*"([^"]+)""".toRegex()

            val strategyIds = regex.findAll(request.strategies_id)
                .map { it.groupValues[1] }
                .toList()

            val updatedStrategies = mutableListOf<Strategy>()

            for (strategyId in strategyIds) {

                val strategy =
                    newStrategiesRepository.findOneByUid(strategyId)

                if (strategy != null && strategy.owner?.uid == user.uid) {
                    updatedStrategies.add(strategy)
                } else if (strategy?.owner?.uid != user.uid) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
                }
            }

            strategies = updatedStrategies
        }

        val updatedPipeline = pipeline.copy(
            title = request.title ?: pipeline.title,
            description = request.description ?: pipeline.description,
            strategies = strategies,
            updatedDate = LocalDateTime.now()
        )

        val savedPipeline = pipelineRepository.save(updatedPipeline)

        log.info("Pipeline updated: {} by user {}", pipelineId, userId)

        return ResponseEntity.ok(savedPipeline)
    }
}