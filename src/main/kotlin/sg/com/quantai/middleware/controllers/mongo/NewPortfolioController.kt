package sg.com.quantai.middleware.controllers.mongo

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.repositories.mongo.NewPortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository

import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.data.mongo.NewPortfolio
import sg.com.quantai.middleware.requests.NewPortfolioRequest

@RestController
@RequestMapping("/newPortfolios")
class NewPortfolioController(
    private val portfolioRepository: NewPortfolioRepository,
    private val userRepository: UserRepository
) {

    private val logger: Logger = LoggerFactory.getLogger(NewPortfolioController::class.java)

    @GetMapping("/user/{user_id}")
    fun getAllPortfoliosByUser(@PathVariable("user_id") userId: String): ResponseEntity<List<NewPortfolio>> {
        val user = userRepository.findOneByUid(userId)
        val userPortfolios = portfolioRepository.findByOwner(user)
        return ResponseEntity.ok(userPortfolios)
    }

}

