package sg.com.quantai.middleware.controllers.mongo

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import org.springframework.dao.EmptyResultDataAccessException
import sg.com.quantai.middleware.data.mongo.*

import sg.com.quantai.middleware.data.mongo.enums.PortfolioActionEnum
import sg.com.quantai.middleware.repositories.mongo.*
import sg.com.quantai.middleware.requests.PortfolioRequest
import sg.com.quantai.middleware.requests.assets.CryptoRequest
import sg.com.quantai.middleware.requests.assets.ForexRequest
import sg.com.quantai.middleware.requests.assets.StockRequest
import java.time.LocalDateTime
import java.math.BigDecimal

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

    @PutMapping("/{user_id}/{portfolio_id}")
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

    fun checkPortfolioAssetQuantity(quantity: BigDecimal, asset: Asset, portfolio: Portfolio): Boolean {
        val portfolioHistory: List<PortfolioHistory> = portfolioHistoryRepository.findByPortfolio(portfolio)
        var portfolioAssetQty = BigDecimal.ZERO
    
        for (history in portfolioHistory) {
            if (history.asset.name == asset.name) {
                if (history.action == PortfolioActionEnum.ADD_MANUAL_ASSET || history.action == PortfolioActionEnum.BUY_REAL_ASSET) {
                    portfolioAssetQty = portfolioAssetQty.add(history.quantity)
                } else {
                    portfolioAssetQty = portfolioAssetQty.subtract(history.quantity)
                }
            }
        }
    
        return portfolioAssetQty.compareTo(quantity) >= 0
    }

    @PostMapping("/asset/stock/update/{user_id}")
    fun updateStock(
        @PathVariable("user_id") user_id: String,
        @RequestBody request: StockRequest
    ) : ResponseEntity<Any> {
        val user = userRepository.findOneByUid(user_id)
        val portfolio = portfolioRepository.findOneByUid(request.portfolio_uid)
        val stock = stockRepository.findByName(request.name)
        val portfolio_main = portfolioRepository.findByOwnerAndMain(user,true)

        if (request.action=="Add"){
            val quantityCheck = checkPortfolioAssetQuantity(request.quantity,stock,portfolio_main)
            if (quantityCheck == false){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot add more than amount in default portfolio.")
            }
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = stock,
                    action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                    quantity = request.quantity,
                    value = request.quantity * request.purchasePrice,
                    portfolio = portfolio,
                )
            )
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = stock,
                    action = PortfolioActionEnum.REMOVE_MANUAL_ASSET,
                    quantity = request.quantity,
                    value = request.quantity * request.purchasePrice,
                    portfolio = portfolio_main,
                )
            )
            return ResponseEntity.status(HttpStatus.OK).body("Transferred ${request.quantity} from default portfolio to ${portfolio.name}.")
        }
        else {
            val quantityCheck = checkPortfolioAssetQuantity(request.quantity,stock,portfolio)
            if (quantityCheck == false){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot remove more than amount in portfolio.")
            }
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = stock,
                    action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                    quantity = request.quantity,
                    value = request.quantity * request.purchasePrice,
                    portfolio = portfolio_main,
                )
            )
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = stock,
                    action = PortfolioActionEnum.REMOVE_MANUAL_ASSET,
                    quantity = request.quantity,
                    value = request.quantity * request.purchasePrice,
                    portfolio = portfolio,
                )
            )
            return ResponseEntity.status(HttpStatus.OK).body("Transferred ${request.quantity} from ${portfolio.name} to default.")
        }
    }

    @PostMapping("/asset/crypto/update/{user_id}")
    fun updateCrypto(
        @PathVariable("user_id") user_id: String,
        @RequestBody request: CryptoRequest
    ) : ResponseEntity<Any> {
        val user = userRepository.findOneByUid(user_id)
        val portfolio = portfolioRepository.findOneByUid(request.portfolio_uid)
        val portfolio_main = portfolioRepository.findByOwnerAndMain(user,true)

        if(!cryptoRepository.existsByName(request.name)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You have no asset of the name ${request.name}")
        }
        
        val crypto = cryptoRepository.findByName(request.name)
        
        if (request.action=="Add"){
            val quantityCheck = checkPortfolioAssetQuantity(request.quantity,crypto,portfolio_main)
            if (quantityCheck == false){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot add more than amount in default portfolio.")
            }
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = crypto,
                    action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                    quantity = request.quantity,
                    value = request.quantity * request.purchasePrice,
                    portfolio = portfolio,
                )
            )
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = crypto,
                    action = PortfolioActionEnum.REMOVE_MANUAL_ASSET,
                    quantity = request.quantity,
                    value = request.quantity * request.purchasePrice,
                    portfolio = portfolio_main,
                )
            )
            return ResponseEntity.status(HttpStatus.OK).body("Transferred ${request.quantity} from default portfolio to ${portfolio.name}.")
        }
        else {
            val quantityCheck = checkPortfolioAssetQuantity(request.quantity,crypto,portfolio)
            if (quantityCheck == false){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot remove more than amount in portfolio.")
            }
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = crypto,
                    action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                    quantity = request.quantity,
                    value = request.quantity * request.purchasePrice,
                    portfolio = portfolio_main,
                )
            )
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = crypto,
                    action = PortfolioActionEnum.REMOVE_MANUAL_ASSET,
                    quantity = request.quantity,
                    value = request.quantity * request.purchasePrice,
                    portfolio = portfolio,
                )
            )
            return ResponseEntity.status(HttpStatus.OK).body("Transferred ${request.quantity} from ${portfolio.name} to default.")
        }
    }
    

    @PostMapping("/asset/forex/update/{user_id}")
    fun updateForex(
        @PathVariable("user_id") user_id: String,
        @RequestBody request: ForexRequest
    ) : ResponseEntity<Any> {
        val user = userRepository.findOneByUid(user_id)
        val portfolio = portfolioRepository.findOneByUid(request.portfolio_uid)
        val forex = forexRepository.findByName(request.name)
        val portfolio_main = portfolioRepository.findByOwnerAndMain(user,true)

        
        if (request.action=="Add"){
            val quantityCheck = checkPortfolioAssetQuantity(request.quantity,forex,portfolio_main)
            if (quantityCheck == false){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot add more than amount in default portfolio.")
            }
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = forex,
                    action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                    quantity = request.quantity,
                    value = request.quantity * request.purchasePrice,
                    portfolio = portfolio,
                )
            )
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = forex,
                    action = PortfolioActionEnum.REMOVE_MANUAL_ASSET,
                    quantity = request.quantity,
                    value = request.quantity * request.purchasePrice,
                    portfolio = portfolio_main,
                )
            )
            return ResponseEntity.status(HttpStatus.OK).body("Transferred ${request.quantity} from default portfolio to ${portfolio.name}.")
        }
        else {
            val quantityCheck = checkPortfolioAssetQuantity(request.quantity,forex,portfolio)
            if (quantityCheck == false){
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot remove more than amount in portfolio.")
            }
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = forex,
                    action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                    quantity = request.quantity,
                    value = request.quantity * request.purchasePrice,
                    portfolio = portfolio_main,
                )
            )
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = forex,
                    action = PortfolioActionEnum.REMOVE_MANUAL_ASSET,
                    quantity = request.quantity,
                    value = request.quantity * request.purchasePrice,
                    portfolio = portfolio,
                )
            )
            return ResponseEntity.status(HttpStatus.OK).body("Transferred ${request.quantity} from ${portfolio.name} to default.")
        }
    }
    
    @DeleteMapping("/user/{user_id}/{portfolio_id}")
    fun deletePortfolioFromUser(
        @PathVariable("user_id") user_id: String,
        @PathVariable("portfolio_id") portfolio_id: String
    ) : ResponseEntity<Any> {
        val user = userRepository.findOneByUid(user_id)
        val portfolio: Portfolio = portfolioRepository.findOneByUidAndOwner(portfolio_id, user)
        if (portfolio.main == true){
            return  ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot delete main portfolio.")
        }
        val portfoliohistory: List<PortfolioHistory> = portfolioHistoryRepository.findByPortfolio(portfolio)
        val portfolio_main = portfolioRepository.findByOwnerAndMain(user,true)

        for (history in portfoliohistory){
            val updatedHistory = history.copy(
                portfolio = portfolio_main,
                updatedDate = LocalDateTime.now()
            )
            portfolioHistoryRepository.save(updatedHistory)
        }

        val portfolio_name = portfolio.name
        portfolioRepository.deleteByUid(portfolio_id)
        return ResponseEntity.ok().body("Deleted portfolio ${portfolio_name}")
    }
}