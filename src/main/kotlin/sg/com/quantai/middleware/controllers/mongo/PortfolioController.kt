package sg.com.quantai.middleware.controllers.mongo

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import sg.com.quantai.middleware.repositories.mongo.PortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository

import sg.com.quantai.middleware.data.mongo.Portfolio
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.requests.PortfolioRequest

@RestController
@RequestMapping("/portfolios")
class PortfolioController(
    private val portfolioRepository: PortfolioRepository,
    private val userRepository: UserRepository
) {

    private val logger: Logger = LoggerFactory.getLogger(PortfolioController::class.java)

    @GetMapping("/user/{user_id}")
    fun getAllPortfoliosByUser(
        @PathVariable("user_id") userId: String
    ): ResponseEntity<List<Portfolio>> {
        val user = userRepository.findOneByUid(userId)

        if (!portfolioRepository.existsByOwnerAndMain(user, true)) {
            portfolioRepository.save(
                Portfolio(
                    main = true,
                    description = "Default portfolio",
                    name = "Default Portfolio",
                    owner = user
                )
            )
        }

        val userPortfolios = portfolioRepository.findByOwner(user)

        return ResponseEntity(userPortfolios, HttpStatus.OK)
    }

    @PostMapping("/{user_id}")
    fun createPortfolio(
        @PathVariable("user_id") userId: String,
        @RequestBody request: PortfolioRequest
    ) : ResponseEntity<Portfolio> {
        val user: User = userRepository.findOneByUid(userId)

        val savedPortfolio = portfolioRepository.save(
            Portfolio(
                description = request.description,
                name = request.name,
                owner = user,
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPortfolio)
    }

}