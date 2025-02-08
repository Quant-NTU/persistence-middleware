package sg.com.quantai.middleware.controllers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.repositories.NewPortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository

import sg.com.quantai.middleware.data.User
import sg.com.quantai.middleware.data.NewPortfolio
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
        val user: User? = userRepository.findOneByUid(userId)

        return user?.let {
            val portfolios = portfolioRepository.findByOwner(it)
            ResponseEntity.ok(portfolios)
        } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }

}

