package sg.com.quantai.middleware.controllers.mongo

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.repositories.mongo.PortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository

import sg.com.quantai.middleware.data.mongo.Portfolio

@RestController
@RequestMapping("/portfolios")
class PortfolioController(
    private val portfolioRepository: PortfolioRepository,
    private val userRepository: UserRepository
) {

    private val logger: Logger = LoggerFactory.getLogger(PortfolioController::class.java)

    @GetMapping("/user/{user_id}")
    fun getAllPortfoliosByUser(@PathVariable("user_id") userId: String): ResponseEntity<List<Portfolio>> {
        val user = userRepository.findOneByUid(userId)
        val userPortfolios = portfolioRepository.findByOwner(user)
        return ResponseEntity.ok(userPortfolios)
    }

}