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

    //Might need to reconfigure to include name of portfolio based on US277 (A portfolio has a name (required) and a description (optional)) but reconfig will be small
    @PostMapping("/{user_id}")
    fun createPortfolio(@PathVariable("user_id") userId: String, @RequestBody request: NewPortfolioRequest): ResponseEntity<NewPortfolio> {
        val user: User? = userRepository.findOneByUid(userId)

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        val existingPortfolios = portfolioRepository.findByOwner(user)
        val defaultPortfolio = existingPortfolios.find { it.isMain }        //Check if the user already has a default portfolio

        //Create a default portfolio for the user if no default portfolio exists
        if (defaultPortfolio == null) {
            val newDefaultPortfolio = NewPortfolio(
                isMain = true,
                owner = user,
                uid = ObjectId.get().toString()
            )
            portfolioRepository.save(newDefaultPortfolio)
        }

        //Create a new portfolio that is not main based on the request
        val newPortfolio = NewPortfolio(
            isMain = false,  
            owner = user,
            uid = ObjectId.get().toString()
        )
        
        val savedPortfolio = portfolioRepository.save(newPortfolio)
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPortfolio)
    }

    @PostMapping("/{user_id}/addAsset")
    fun addAssetToPortfolio(@PathVariable("user_id") userId: String, @RequestBody request: AddAssetRequest): ResponseEntity<NewPortfolio> {
        val user: User? = userRepository.findOneByUid(userId)
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        val portfolio = portfolioRepository.findByOwner(user).firstOrNull()
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        
        //adding asset to portfolio depending on the type of asset
        val asset: Asset = when (request.type) {
            "crypto" -> NewCrypto(
                name = request.name,
                symbol = request.symbol,
                quantity = request.quantity,
                purchasePrice = request.purchasePrice
            ).also { cryptoRepository.save(it) }
            "stock" -> NewStock(
                name = request.name,
                symbol = request.symbol,
                quantity = request.quantity,
                purchasePrice = request.purchasePrice
            ).also { stockRepository.save(it) }
            else -> return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }

        //Creating a portfolio history "add" record 
        val newHistory = PortfolioHistory(
            asset = asset,
            action = PortfolioAction.ADD,
            quantity = request.quantity,
            value = request.purchasePrice.multiply(request.quantity),
            owner = portfolio
        )
        portfolioHistoryRepository.save(newHistory)

        val updatedPortfolio = portfolio.copy(
            history = portfolio.history.orEmpty() + newHistory,
            assets = portfolio.assets.orEmpty() + asset
        )
        portfolioRepository.save(updatedPortfolio)

        return ResponseEntity.status(HttpStatus.OK).body(updatedPortfolio)
    }

    @DeleteMapping("/{portfolio_id}/removeAsset")
    fun removeAssetFromPortfolio(@PathVariable("portfolio_id") portfolioId: String, @RequestBody request: RemoveAssetRequest): ResponseEntity<NewPortfolio> {
        val portfolio = portfolioRepository.findByUid(portfolioId) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val assetToRemove = portfolio.assets?.find { it.symbol == request.symbol }
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        //To check if the quantity to remove is valid to prevent negative
        if (request.quantity > assetToRemove.quantity) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null)
        }

        val updatedAssetList = if (request.quantity == assetToRemove.quantity) {
            portfolio.assets.filter { it.symbol != request.symbol } //Remove the asset entirely
        } else {
            portfolio.assets.map {
                if (it.symbol == request.symbol) it.copy(quantity = it.quantity - request.quantity) else it
            }
        }

        //Create a new addition to PortfolioHistory
        val newHistory = PortfolioHistory(
            asset = assetToRemove,
            action = PortfolioAction.REMOVE,
            quantity = request.quantity,
            //To ensure we have a historical reference to calculate PnL later
            value = assetToRemove.purchasePrice * request.quantity, 
            owner = portfolio
        )

        val updatedPortfolio = portfolio.copy(
            assets = updatedAssetList,
            history = portfolio.history?.plus(newHistory)
        )

        portfolioRepository.save(updatedPortfolio)

        return ResponseEntity.ok(updatedPortfolio)
    }

}

