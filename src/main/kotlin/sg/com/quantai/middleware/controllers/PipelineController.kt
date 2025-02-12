package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.repositories.mongo.UserRepository
import sg.com.quantai.middleware.data.Strategy
import sg.com.quantai.middleware.repositories.mongo.StrategyRepository
import sg.com.quantai.middleware.data.Pipeline
import sg.com.quantai.middleware.repositories.mongo.PipelineRepository
import sg.com.quantai.middleware.requests.PipelineRequest

import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/pipelines")
class PipelineController(
    private val pipelineRepository: PipelineRepository,
    private val userRepository: UserRepository,
    private val newStrategiesRepository: StrategyRepository
) {
    private val log = LoggerFactory.getLogger(PipelineController::class.java)

    @GetMapping("")
    fun getAllPipelines(): ResponseEntity<List<Pipeline>> {
        val pipeline = pipelineRepository.findAll()
        return ResponseEntity.ok(pipeline)
    }

    @GetMapping("/user/{user_id}")
    fun getUserPipelines( @PathVariable("user_id") userId: String): ResponseEntity<List<Pipeline>> {
        val user = userRepository.findOneByUid(userId)
        val userPipelines = pipelineRepository.findByOwner(user)
        return ResponseEntity.ok(userPipelines)
    }

    @GetMapping("/user/{user_id}/{pipeline_id}")
    fun getOnePipelineFromUser(
        @PathVariable("user_id") user_id: String,
        @PathVariable("pipeline_id") pipeline_id: String
    ) : ResponseEntity<Pipeline> {
        val pipeline = pipelineRepository.findOneByUid(pipeline_id)
        return ResponseEntity.ok(pipeline)
    }

    @PostMapping("/{user_id}")
    fun createPipeline(
        @PathVariable("user_id") user_id: String,
        @RequestBody request: PipelineRequest
    ): ResponseEntity<Pipeline> {
        val user = userRepository.findOneByUid(user_id)
        // val portfolio = portfolioRepository.findOneByUid(request.portfolio_id)

        var strategies = emptyList<Strategy>()

        if (request.strategies_id.isNotBlank()) {
            val regex = """strategyId"\s*:\s*"([^"]+)""".toRegex()
            val strategyIds = regex.findAll(request.strategies_id).map { it.groupValues[1] }.toList()
        
            strategies = strategyIds.mapNotNull { strategyId ->
                val strategy = newStrategiesRepository.findOneByUid(strategyId)
                if (strategy != null && strategy.owner?.uid == user.uid) {
                    strategy
                } else if (strategy?.owner?.uid != user.uid) {
                    return ResponseEntity(HttpStatus.FORBIDDEN)
                } else {
                    null
                }
            }
        }      

        val pipeline =
                pipelineRepository.save(
                    Pipeline(
                        title = request.title,
                        owner = user,
                        description = request.description,
                        // portfolio = portfolio,
                        strategies = strategies,
                        createdDate = LocalDateTime.now()
                    )
                )
        return ResponseEntity(pipeline, HttpStatus.CREATED)
    }

    @DeleteMapping("/user/{user_id}/{pipeline_id}")
    fun deletePipelineFromUser(
        @PathVariable("user_id") user_id: String,
        @PathVariable("pipeline_id") pipeline_id: String
    ) : ResponseEntity<Any> {
        val user = userRepository.findOneByUid(user_id)
        val pipeline = pipelineRepository.findOneByUid(pipeline_id)
        if (pipeline.owner.uid == user.uid) {
            pipelineRepository.deleteByUid(pipeline.uid)
        }
        else{
            return ResponseEntity(HttpStatus.FORBIDDEN)
        }
        return ResponseEntity.ok().body("Deleted pipeline ${pipeline_id}")
    }

    @PutMapping("/user/{user_id}/{pipeline_id}")
    fun updatePipeline(
        @PathVariable("user_id") user_id: String,
        @PathVariable("pipeline_id") pipeline_id: String,
        @RequestBody request: PipelineRequest
    ): ResponseEntity<Pipeline> {
        val user = userRepository.findOneByUid(user_id)
        val pipeline = pipelineRepository.findOneByUid(pipeline_id)

        if (pipeline.owner.uid != user.uid) {
            return ResponseEntity(HttpStatus.FORBIDDEN)
        }

        var strategies = emptyList<Strategy>()

        if (request.strategies_id.isNotBlank()) {
            val regex = """strategyId"\s*:\s*"([^"]+)""".toRegex()
            val strategyIds = regex.findAll(request.strategies_id).map { it.groupValues[1] }.toList()
            strategies = strategyIds.mapNotNull { strategyId ->
                val strategy = newStrategiesRepository.findOneByUid(strategyId)
                if (strategy != null && strategy.owner?.uid == user.uid) {
                    strategy
                } else if (strategy?.owner?.uid != user.uid) {
                    return ResponseEntity(HttpStatus.FORBIDDEN)
                } else {
                    null
                }
            }
        }      

        val updatedPipeline = pipeline.copy(
            title = request.title ?: pipeline.title,
            description = request.description ?: pipeline.description,
            strategies = strategies?: pipeline.strategies,
            updatedDate = LocalDateTime.now()
        )

        val savedPipeline = pipelineRepository.save(updatedPipeline)

        return ResponseEntity.ok(savedPipeline)
    }


}
    
    

    