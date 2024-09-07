package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.repositories.PortfolioRepository
import sg.com.quantai.middleware.repositories.TransactionRepository
import sg.com.quantai.middleware.data.User
import sg.com.quantai.middleware.repositories.UserRepository
import sg.com.quantai.middleware.repositories.CryptoRepository
import sg.com.quantai.middleware.repositories.StockRepository
import sg.com.quantai.middleware.data.Portfolio
import sg.com.quantai.middleware.requests.PortfolioRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Async
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode

@RestController
@RequestMapping("/portfolio")
class PortfolioController(
    private val portfolioRepository: PortfolioRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val cryptoRepository: CryptoRepository,
    private val stockRepository: StockRepository
) {

    private val logger: Logger = LoggerFactory.getLogger(AccountInfoController::class.java)

    // Retrieve all transactions from a user
    @GetMapping("/user/{user_id}")
    fun retrieveAllPortfolioFromUser(@PathVariable("user_id") user_id: String): ResponseEntity<List<Portfolio>> {
        val user = userRepository.findOneByUid(user_id)
        val portfolios = portfolioRepository.findByOwner(user)
        // Temporary solution to update the portfolio based on the transaction history while retrieving all portfolios
        // TODO: to set up a scheduler to update the portfolio regularly
        updatePortfolioForUser(user)
        return ResponseEntity.ok(portfolios)
    }

    // Get a user's portfolio by symbol
    @GetMapping("/user/{user_id}/{symbol}")
    fun getUserPortfolioBySymbol(
        @PathVariable("user_id") user_id: String,
        @PathVariable("symbol") symbol: String
    ): ResponseEntity<List<Portfolio>> {
        val user = userRepository.findOneByUid(user_id)
        val portfolios = portfolioRepository.findByOwnerAndSymbol(user, symbol)
        return ResponseEntity.ok(portfolios)
    }

    //Create portfolio into user's profile
    @PostMapping("/{uid}")
    fun createPortfolio(
        @RequestBody request: PortfolioRequest,
        @PathVariable("uid") user_id: String
    ): ResponseEntity<Portfolio> {
        val user = userRepository.findOneByUid(user_id)
        val portfolios = portfolioRepository.findByOwner(user)
        for (item in portfolios){
            if (request.symbol == item.symbol && request.platform == item.platform) {
                //aggregation
                val itemPrice = BigDecimal(item.price.toString())
                val itemQuantity = BigDecimal(item.quantity.toString())
                val requestQuantity = BigDecimal(request.quantity.toString())
                val requestPrice = BigDecimal(request.price.toString())

                val updatedQuantity = item.quantity + request.quantity
                val updatedPrice = ((itemPrice * itemQuantity) + (requestQuantity * requestPrice))
                    .divide(updatedQuantity, 2, RoundingMode.HALF_UP)

                val portfolio = 
                portfolioRepository.save(
                        Portfolio(
                                symbol = item.symbol,
                                name = item.name,
                                quantity = updatedQuantity,
                                price = updatedPrice,
                                platform = item.platform,
                                owner = user,
                                uid = item.uid,
                                _id = item._id
                        )
                )
                return ResponseEntity(portfolio, HttpStatus.CREATED);
            }
        }
        val portfolio = 
                portfolioRepository.save(
                        Portfolio(
                                symbol = request.symbol,
                                name = request.name,
                                quantity = request.quantity,
                                price = request.price,
                                platform = request.platform,
                                owner = user
                        )
                )
        return ResponseEntity(portfolio, HttpStatus.CREATED)
    }

    @Async
    fun updatePortfolioForUser(user: User){
        /**
         * Update Portfolio for all symbols based on a user's transaction history
         * Triggered in retrieveAllPortfolioFromUser() while retrieving an user's portfolios in the frontend
         * TODO: to set up a scheduler to update the portfolio regularly
         */

        // Get all symbols for the user from transaction history
        var transactions = transactionRepository.findByOwner(user)
        logger.info("Updating portfolio for user ${user.uid} according to ${transactions.size} transactions")

        // Get all symbols for the user from transaction history
        var symbols = mutableListOf<String>()
        transactions.forEach{ transaction ->
            if (transaction.stock != null) {
                symbols.add(transaction.stock.symbol)
            } else
            if (transaction.crypto != null) {
                symbols.add(transaction.crypto.symbol)
            }
        }

        // Get Current Prices for user's total value for relevant assets
        val cryptos = cryptoRepository.findBySymbolIn(symbols)
        val stocks = stockRepository.findBySymbolIn(symbols)

        // Create portfolio entries for each symbol if not exists
        for (symbol in symbols){
            val portfolio = portfolioRepository.findByOwnerAndSymbol(user, symbol)
            val crypto = cryptos.find { it.symbol == symbol }
            val stock = stocks.find { it.symbol == symbol }
            val price = crypto?.price ?: stock?.price
            val name = crypto?.name ?: stock?.name

            if (portfolio.isEmpty()){
                // Create portfolio entries for new symbols
                val platform = if (crypto != null) "crypto" else "stock"
                portfolioRepository.save(
                        Portfolio(
                                symbol = symbol,
                                name = name!!,
                                quantity = BigDecimal(0.0),
                                price = price!!,
                                platform = platform,
                                owner = user
                        )
                )
            } else {
                // Update portfolio entries for existing symbols
                val existingPortfolio = portfolioRepository.findByOwnerAndSymbol(user, symbol).first()
                val transactionsForSymbol = transactions.filter { it.stock?.symbol == symbol || it.crypto?.symbol == symbol }
                var updatedQuantity = BigDecimal(0.0)
                transactionsForSymbol.forEach{ transaction ->
                    if (transaction.stock != null) {
                        updatedQuantity += transaction.quantity
                    } else
                    if (transaction.crypto != null) {
                        updatedQuantity += transaction.quantity
                    }
                }
                portfolioRepository.save(
                        Portfolio(
                                symbol = symbol,
                                name = name!!,
                                quantity = updatedQuantity,
                                price = price!!,
                                platform = existingPortfolio.platform,
                                owner = user,
                                uid = existingPortfolio.uid,
                                _id = existingPortfolio._id
                        )
                )
            }
        }
        logger.info("Portfolio updated for user ${user.uid} with ${symbols.size} symbols")
    }


}


