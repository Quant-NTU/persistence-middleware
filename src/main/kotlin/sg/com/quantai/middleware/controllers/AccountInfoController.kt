package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.AccountInfo
import sg.com.quantai.middleware.repositories.mongo.AccountInfoRepository
import sg.com.quantai.middleware.repositories.mongo.PortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.CryptoRepository
import sg.com.quantai.middleware.repositories.mongo.StockRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/accountinfo")
class AccountInfoController(
    private val accountInfoRepository: AccountInfoRepository,
    private val portfolioRepository: PortfolioRepository,
    private val cryptoRepository: CryptoRepository,
    private val stockRepository: StockRepository,
    private val userRepository: UserRepository
) {

    private val logger: Logger = LoggerFactory.getLogger(AccountInfoController::class.java)

    // Retrieve account info from a user
    @GetMapping("/user/{user_id}")
    fun getAccountInfoByUser(
        @PathVariable("user_id") user_id: String
    ): ResponseEntity<Any> {
        try {
            val user = userRepository.findOneByUid(user_id)

            return try {
                val accountInfo = accountInfoRepository.findByOwner(user)
                ResponseEntity.ok(accountInfo)

            } catch (ex: EmptyResultDataAccessException) {
                // TODO: account info is planned to be created and managed by a scheduled task
                // Create a new account info for the user if it doesn't exist as a temporary solution
                logger.info("Creating account info for user with id: $user_id")
                createAccountInfo(user_id)
                val accountInfo = accountInfoRepository.findByOwner(user)
                ResponseEntity.ok(accountInfo)
            }

        } catch (ex: EmptyResultDataAccessException) {
            logger.error("User not found with id: $user_id")
            return ResponseEntity.status(404).body("User not found")
        }
    }

    // Get an account info by id
    @GetMapping("/{uid}")
    fun getAccountInfoById(
        @PathVariable("uid") uid: String
    ): ResponseEntity<AccountInfo> {
        val accountInfo = accountInfoRepository.findOneByUid(uid)
        return ResponseEntity.ok(accountInfo)
    }

    // Delete account info
    @DeleteMapping("/user/{user_id}")
    fun deleteAccountInfo(
        @PathVariable("user_id") user_id: String
    ): ResponseEntity<AccountInfo> {
        val user = userRepository.findOneByUid(user_id)
        accountInfoRepository.deleteByOwner(user)
        return ResponseEntity.noContent().build()
    }

    // Update account info
    @PutMapping("/user/{user_id}")
    fun updateAccountInfo(
            @PathVariable("user_id") user_id: String
    ): ResponseEntity<AccountInfo> {
        //Get User
        val user = userRepository.findOneByUid(user_id)
        val portfolios = portfolioRepository.findByOwner(user)

        //Get User's Portfolio
        var symbols = mutableListOf<String>()
        portfolios.forEach{ portfolio ->
            symbols.add(portfolio.symbol)
        }

        //Get Current Prices for user's total value for relevant assets
        var cryptos = cryptoRepository.findBySymbolIn(symbols)
        var stocks = stockRepository.findBySymbolIn(symbols)
        var totalValue = BigDecimal(0.0)

        portfolios.forEach{ portfolio ->
            val matchingCrypto = cryptos.find { it.symbol == portfolio.symbol }
            if (matchingCrypto != null) {
                totalValue += portfolio.quantity * matchingCrypto.price
            } else {
                val matchingStock = stocks.find { it.symbol == portfolio.symbol }
                if (matchingStock != null) {
                    totalValue += portfolio.quantity * matchingStock.price
                }
            }
        }

        //Update latest AccountValue
        val accountInfo = accountInfoRepository.findByOwner(user)
        val newAccountValue = accountInfo.accountValue
        newAccountValue.removeAt(0)
        newAccountValue.add(totalValue)

        val updatedAccountInfo =
                accountInfoRepository.save(
                        AccountInfo(
                                accountValue = newAccountValue,
                                owner = accountInfo.owner,
                                _id = accountInfo._id,
                                uid = accountInfo.uid
                        )
                )
        return ResponseEntity.ok(updatedAccountInfo)
    }

    // Create Account Info
    @PostMapping("/user/{user_id}")
    fun createAccountInfo(
        @PathVariable("user_id") user_id: String
    ): ResponseEntity<AccountInfo> {
        val user = userRepository.findOneByUid(user_id)
        val newAccountInfo =
                accountInfoRepository.save(
                        AccountInfo(
                                owner = user
                        )
                )
        logger.info("Account info created for user with id: $user_id")
        return ResponseEntity(newAccountInfo, HttpStatus.CREATED)
    }
}
