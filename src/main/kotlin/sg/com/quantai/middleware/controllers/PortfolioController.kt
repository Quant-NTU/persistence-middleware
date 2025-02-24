package sg.com.quantai.middleware.controllers

import org.bson.types.ObjectId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.repositories.NewPortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository

import sg.com.quantai.middleware.data.User
import sg.com.quantai.middleware.data.Portfolio
import sg.com.quantai.middleware.requests.PortfolioRequest

@RestController
@RequestMapping("/newPortfolios")
class PortfolioController(
    private val portfolioRepository: NewPortfolioRepository,
    private val userRepository: UserRepository
) {

    private val logger: Logger = LoggerFactory.getLogger(PortfolioController::class.java)

    @GetMapping("/user/{user_id}")
    fun getAllPortfoliosByUser(@PathVariable("user_id") userId: String): ResponseEntity<List<Portfolio>> {
        val user: User? = userRepository.findOneByUid(userId)

        return user?.let {
            val portfolios = portfolioRepository.findByOwner(it)
            ResponseEntity.ok(portfolios)
        } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }

    //Might need to reconfigure to include description of portfolio based on US277 description (optional))
    @PostMapping("/{user_id}")
    fun createPortfolio(@PathVariable("user_id") userId: String, @RequestBody request: NewPortfolioRequest): ResponseEntity<Portfolio> {
        val user: User? = userRepository.findOneByUid(userId)

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        val existingPortfolios = portfolioRepository.findByOwner(user)
        val defaultPortfolio = existingPortfolios.find { it.isMain }        //Check if the user already has a default portfolio

        //Create a default portfolio for the user if no default portfolio exists
        if (defaultPortfolio == null) {
            val newDefaultPortfolio = Portfolio(
                name = "Main Portfolio", //Not sure if user should be allowed to edit this 
                isMain = true,
                owner = user,
                uid = ObjectId.get().toString()
            )
            portfolioRepository.save(newDefaultPortfolio)
        }

        //Create a new portfolio that is not main based on the request
        val newPortfolio = Portfolio(
            name = request.name,
            isMain = false,  
            owner = user,
            uid = ObjectId.get().toString()
        )
        
        val savedPortfolio = portfolioRepository.save(newPortfolio)
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPortfolio)
    }

}

