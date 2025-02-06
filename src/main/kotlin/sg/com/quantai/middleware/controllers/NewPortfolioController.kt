package sg.com.quantai.middleware.controllers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
//Database query management imports
import sg.com.quantai.middleware.repositories.NewPortfolioRepository //Handles database operations for portfolios. Might need to change back to portfolio repository, not sure
import sg.com.quantai.middleware.repositories.UserRepository //Handles database operations for users

import sg.com.quantai.middleware.data.User
import sg.com.quantai.middleware.data.NewPortfolio
import sg.com.quantai.middleware.requests.NewPortfolioRequest //Are we creating a new one? Same as NewPortfolioRepository

@RestController
@RequestMapping("/new-portfolio")
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

    @PostMapping("/{user_id}")
    fun createPortfolio(
        @RequestBody request: NewPortfolioRequest,
        @PathVariable("user_id") userId: String
    ): ResponseEntity<NewPortfolio> {
        
        val user: User? = userRepository.findOneByUid(userId)

        return user?.let {
            val portfolio = NewPortfolio(
                isMain = request.isMain(),
                history = request.getHistory(),
                owner = it
            )
            ResponseEntity.status(HttpStatus.CREATED).body(portfolioRepository.save(portfolio))
        } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }
}

