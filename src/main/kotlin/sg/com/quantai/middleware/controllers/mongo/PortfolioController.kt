package sg.com.quantai.middleware.controllers.mongo

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import sg.com.quantai.middleware.data.mongo.*

import sg.com.quantai.middleware.data.mongo.enums.PortfolioActionEnum
import sg.com.quantai.middleware.repositories.mongo.*
import sg.com.quantai.middleware.requests.PortfolioRequest
import sg.com.quantai.middleware.requests.assets.CryptoRequest
import sg.com.quantai.middleware.requests.assets.ForexRequest
import sg.com.quantai.middleware.requests.assets.StockRequest
import java.time.LocalDateTime

@RestController
@RequestMapping("/portfolios")
class PortfolioController(
    private val portfolioRepository: PortfolioRepository,
    private val portfolioHistoryRepository: PortfolioHistoryRepository,
    private val cryptoRepository: CryptoRepository,
    private val stockRepository: StockRepository,
    private val forexRepository: ForexRepository,
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

    data class PortfolioHistoryResponse(
        val history: PortfolioHistory,
        val assetType: String
    )

    @GetMapping("/history/{user_id}/{portfolio_id}")
    fun getAllPortfolioHistoryByPortfolio(
        @PathVariable("user_id") userId: String,
        @PathVariable("portfolio_id") portfolioId: String
    ): ResponseEntity<List<PortfolioHistoryResponse>> {
        val user = userRepository.findOneByUid(userId)
        val portfolio: Portfolio = portfolioRepository.findOneByUidAndOwner(portfolioId, user)
        val portfoliohistory: List<PortfolioHistory> = portfolioHistoryRepository.findByPortfolio(portfolio)

        val response = portfoliohistory.map { history ->
            PortfolioHistoryResponse(
                history = history,
                assetType = when (history.asset) {
                    is Forex -> "Forex"
                    is Crypto -> "Crypto"
                    is Stock -> "Stock"
                    else -> "Unknown"
                }
            )
        }
    
        return ResponseEntity(response, HttpStatus.OK)
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

    @PatchMapping("/{user_id}/{portfolio_id}")
    fun updatePortfolio(
        @PathVariable("user_id") userId: String,
        @PathVariable("portfolio_id") portfolioId: String,
        @RequestBody request: PortfolioRequest
    ) : ResponseEntity<Portfolio> {
        val user: User = userRepository.findOneByUid(userId)
        val portfolio: Portfolio = portfolioRepository.findOneByUidAndOwner(portfolioId, user)

        val savedPortfolio = portfolioRepository.save(
            Portfolio(
                _id = portfolio._id,
                uid = portfolio.uid,
                main = portfolio.main,
                description = request.description,
                name = request.name,
                createdDate = portfolio.createdDate,
                updatedDate = LocalDateTime.now(),
                owner = user
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPortfolio)
    }

    @PostMapping("/asset/crypto/{portfolio_id}")
    fun addCrypto(
        @PathVariable("portfolio_id") portfolioId: String,
        @RequestBody request: CryptoRequest
    ) : ResponseEntity<Crypto> {
        val portfolio: Portfolio = portfolioRepository.findOneByUid(request.portfolio_uid)

        if (!cryptoRepository.existsByName(request.name)) {
            cryptoRepository.save(
                Crypto(
                    name = request.name,
                    quantity = request.quantity,
                    purchasePrice = request.purchasePrice,
                    symbol = request.symbol
                )
            )
        }
        val crypto = cryptoRepository.findByName(request.name)

        portfolioHistoryRepository.save(
            PortfolioHistory(
                asset = crypto,
                action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                quantity = request.quantity,
                value = request.quantity * request.purchasePrice,
                portfolio = portfolio,
            )
        )

        return ResponseEntity.status(HttpStatus.OK).body(crypto)
    }

    @PostMapping("/asset/stock/{portfolio_id}")
    fun addStock(
        @PathVariable("portfolio_id") portfolioId: String,
        @RequestBody request: StockRequest
    ) : ResponseEntity<Stock> {
        val portfolio: Portfolio = portfolioRepository.findOneByUid(request.portfolio_uid)

        if (!stockRepository.existsByName(request.name)) {
            stockRepository.save(
                Stock(
                    name = request.name,
                    quantity = request.quantity,
                    purchasePrice = request.purchasePrice,
                    ticker = request.ticker
                )
            )
        }
        val stock = stockRepository.findByName(request.name)

        portfolioHistoryRepository.save(
            PortfolioHistory(
                asset = stock,
                action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                quantity = request.quantity,
                value = request.quantity * request.purchasePrice,
                portfolio = portfolio,
            )
        )

        return ResponseEntity.status(HttpStatus.OK).body(stock)
    }

    @PostMapping("/asset/forex/{portfolio_id}")
    fun addForex(
        @PathVariable("portfolio_id") portfolioId: String,
        @RequestBody request: ForexRequest
    ) : ResponseEntity<Forex> {
        val portfolio: Portfolio = portfolioRepository.findOneByUid(request.portfolio_uid)

        if (!forexRepository.existsByName(request.name)) {
            forexRepository.save(
                Forex(
                    name = request.name,
                    quantity = request.quantity,
                    purchasePrice = request.purchasePrice,
                    currencyPair = request.currencyPair,
                )
            )
        }
        val forex = forexRepository.findByName(request.name)

        portfolioHistoryRepository.save(
            PortfolioHistory(
                asset = forex,
                action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                quantity = request.quantity,
                value = request.quantity * request.purchasePrice,
                portfolio = portfolio,
            )
        )

        return ResponseEntity.status(HttpStatus.OK).body(forex)
    }
}