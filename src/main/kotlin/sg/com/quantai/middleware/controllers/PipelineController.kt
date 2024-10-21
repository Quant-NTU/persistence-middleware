package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.User
import sg.com.quantai.middleware.repositories.UserRepository
import sg.com.quantai.middleware.data.NewStrategy
import sg.com.quantai.middleware.repositories.NewStrategyRepository
import sg.com.quantai.middleware.repositories.PortfolioRepository
import sg.com.quantai.middleware.data.Portfolio
import sg.com.quantai.middleware.data.Pipeline
import sg.com.quantai.middleware.repositories.PipelineRepository
import sg.com.quantai.middleware.requests.PipelineRequest

import java.time.LocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Async
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/pipelines")
class PipelineController(
    private val pipelineRepository: PipelineRepository,
    private val userRepository: UserRepository,
    private val newStrategiesRepository: NewStrategyRepository,
    private val portfolioRepository: PortfolioRepository,
) {
    private val log = LoggerFactory.getLogger(PipelineController::class.java)

    // Retrieve all pipelines
    @GetMapping("")
    fun getAllPipelines(): ResponseEntity<List<Pipeline>> {
        val pipeline = pipelineRepository.findAll()
        return ResponseEntity.ok(pipeline)
    }

    // Retrieve all pipelines from a user
    @GetMapping("/user/{user_id}")
    fun getUserPipelines( @PathVariable("user_id") userId: String): ResponseEntity<List<Pipeline>> {
        val user = userRepository.findOneByUid(userId)
        val userPipelines = pipelineRepository.findByOwner(user)
        return ResponseEntity.ok(userPipelines)
    }

    // Retrieve one pipeline from a user
    @GetMapping("/user/{user_id}/{pipeline_id}")
    fun getOnePipelineFromUser(
        @PathVariable("user_id") user_id: String,
        @PathVariable("pipeline_id") pipeline_id: String
    ) : ResponseEntity<Pipeline> {
        val pipeline = pipelineRepository.findOneByUid(pipeline_id)
        return ResponseEntity.ok(pipeline)
    }

    // Create Pipeline
    @PostMapping("/{user_id}")
    fun createPipeline(
        @PathVariable("user_id") user_id: String,
        @RequestBody request: PipelineRequest
    ): ResponseEntity<Pipeline> {
        val user = userRepository.findOneByUid(user_id)
        val portfolio = portfolioRepository.findOneByUid(request.portfolio_id)
        val pipeline =
                pipelineRepository.save(
                    Pipeline(
                        title = request.title,
                        owner = user,
                        description = request.description,
                        portfolio = portfolio,
                        execution_method = request.execution_method,
                    )
                )
        return ResponseEntity(pipeline, HttpStatus.CREATED)
    }

    // Delete a pipeline
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
        return ResponseEntity.noContent().build()
    }

    // Update a pipeline
    @PutMapping("/user/{user_id}/{pipeline_id}")
    fun updatePipeline(
        @PathVariable("user_id") user_id: String,
        @PathVariable("pipeline_id") pipeline_id: String,
        @RequestBody request: PipelineRequest
    ): ResponseEntity<Pipeline> {
        val user = userRepository.findOneByUid(user_id)
        val pipeline = pipelineRepository.findOneByUid(pipeline_id)

        // Check if the pipeline belongs to the user
        if (pipeline.owner.uid != user.uid) {
            return ResponseEntity(HttpStatus.FORBIDDEN)
        }

        var strategies = emptyList<NewStrategy>()

        if (request.strategies_id.isNotBlank()) {
            // Split the comma-separated strategy_ids into a list
            val strategyIds = request.strategies_id.split(",").map { it.trim() }
        
            // Fetch strategies from the repository based on the ids
            strategies = strategyIds.mapNotNull { strategyId ->
                val strategy = newStrategiesRepository.findOneByUid(strategyId)
                if (strategy != null && strategy.owner?.uid == user.uid) {
                    strategy
                } else if (strategy?.owner?.uid != user.uid) {
                    return ResponseEntity(HttpStatus.FORBIDDEN) // Handle unauthorized access
                } else {
                    null
                }
            }
        }      

        // Update fields
        val updatedPipeline = pipeline.copy(
            title = request.title ?: pipeline.title,
            description = request.description ?: pipeline.description,
            strategies = strategies?: pipeline.strategies,
            execution_method = request.execution_method?: pipeline.execution_method,
            updatedDate = LocalDateTime.now() // Update the timestamp
        )

        // Save the updated pipeline to the repository
        val savedPipeline = pipelineRepository.save(updatedPipeline)

        return ResponseEntity.ok(savedPipeline)
    }


}
    
    

    