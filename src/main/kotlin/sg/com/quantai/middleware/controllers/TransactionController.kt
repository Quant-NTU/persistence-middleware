package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.Transaction
import sg.com.quantai.middleware.data.Crypto
import sg.com.quantai.middleware.data.Stock
import sg.com.quantai.middleware.data.enums.TransactionStatus
import sg.com.quantai.middleware.requests.TransactionRequest
import sg.com.quantai.middleware.repositories.mongo.TransactionRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import sg.com.quantai.middleware.repositories.mongo.CryptoRepository
import sg.com.quantai.middleware.repositories.mongo.StockRepository
import java.math.BigDecimal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.zeromq.ZMQ
import java.time.LocalDateTime

@RestController
@RequestMapping("/transactions")
class TransactionController(
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
    private val cryptoRepository: CryptoRepository,
    private val stockRepository: StockRepository
) {
    private val zmqContext = ZMQ.context(1)
    private val publisherSocket = zmqContext.socket(ZMQ.PUB)

    init {
        try {
            publisherSocket.connect("tcp://message-queue:5556")
        } catch (e: Exception) {
            println("Error connecting to message queue: ${e.message}")
        }
    }
    // Create a function to publish messages to the ZeroMQ message queue
    private fun publishMessage(topic: String, message: String) {
        publisherSocket.sendMore(topic)
        publisherSocket.send(message)
    }

    // Retrieve all transactions from a user
    @GetMapping("/user/{user_id}")
    fun getAllTransactionsFromUser(
        @PathVariable("user_id") user_id: String
    ): ResponseEntity<List<Transaction>> {
        val user = userRepository.findOneByUid(user_id)
        val transactions = transactionRepository.findByOwner(user)
        return ResponseEntity.ok(transactions)
    }

    // Retrieve all transactions from a crypto
    @GetMapping("/crypto/{crypto_id}")
    fun getAllTransactionsFromCrypto(
        @PathVariable("crypto_id") crypto_id: String
    ): ResponseEntity<List<Transaction>> {
        val crypto = cryptoRepository.findOneByUuid(crypto_id)
        val transactions = transactionRepository.findByCrypto(crypto)
        return ResponseEntity.ok(transactions)
    }

    // Retrieve all transactions from a stock
    @GetMapping("/stock/{stock_id}")
    fun getAllTransactionsFromStock(
            @PathVariable("stock_id") stock_id: String
    ): ResponseEntity<List<Transaction>> {
        val stock = stockRepository.findOneByUuid(stock_id)
        val transactions = transactionRepository.findByStock(stock)
        return ResponseEntity.ok(transactions)
    }

// TODO: Deprecated
//
//    @GetMapping("strategy/{strategy_id}")
//    fun getAllTransactionsFromStrategy(
//        @PathVariable("strategy_id") strategy_id: String
//    ): ResponseEntity<List<Transaction>> {
//        val strategy = strategyRepository.findOneByUid(strategy_id)
//        if (strategy == null) {
//            return ResponseEntity(HttpStatus.NOT_FOUND)
//        }
//        val transactions = transactionRepository.findByStrategy(strategy)
//        return ResponseEntity.ok(transactions)
//    }

    // Get a single transaction by id
    @GetMapping("/{uid}")
    fun getOneTransaction(
        @PathVariable("uid") uid: String
    ): ResponseEntity<Transaction> {
        val transaction = transactionRepository.findOneByUid(uid)
        return ResponseEntity.ok(transaction)
    }

    @GetMapping("/period/{days}")
    fun getTransactionsWithinPeriod(
        @PathVariable("days") days: Long
    ): ResponseEntity<List<Transaction>> {
        val currentDate = LocalDateTime.now()
        val startDate = currentDate.minusDays(days)
        val transactions = transactionRepository.findByCreatedDateBetween(startDate, currentDate)
        return ResponseEntity.ok(transactions)
    }

    // Get count of each type of transaction
    @GetMapping("/count")
    fun countTransactions(): ResponseEntity<Map<String, Long>> {
        val pendingCount = transactionRepository.countByStatus(TransactionStatus.PENDING)
        val rejectedCount = transactionRepository.countByStatus(TransactionStatus.REJECTED)
        val filledCount = transactionRepository.countByStatus(TransactionStatus.FILLED)

        val countMap = mapOf(
            "pendingOrders" to pendingCount,
            "rejectedOrders" to rejectedCount,
            "filledOrders" to filledCount,
        )

        return ResponseEntity.ok(countMap)
    }

    // Get count of each type of transaction within a specified number of days
    @GetMapping("/count/period/{days}")
    fun countTransactions(
        @PathVariable("days") days: Long
    ): ResponseEntity<Map<String, Long>> {
        val currentDate = LocalDateTime.now()
        val startDate = currentDate.minusDays(days)
        val pendingCount = transactionRepository.countByStatusAndCreatedDateBetween(TransactionStatus.PENDING, startDate, currentDate)
        val rejectedCount = transactionRepository.countByStatusAndCreatedDateBetween(TransactionStatus.REJECTED, startDate, currentDate)
        val filledCount = transactionRepository.countByStatusAndCreatedDateBetween(TransactionStatus.FILLED, startDate, currentDate)

        val countMap = mapOf(
            "pendingOrders" to pendingCount,
            "rejectedOrders" to rejectedCount,
            "filledOrders" to filledCount,
        )

        return ResponseEntity.ok(countMap)
    }

    // Delete transaction
    @DeleteMapping("/{uid}")
    fun deleteTransaction(
        @PathVariable("uid") uid: String
    ): ResponseEntity<Transaction> {
        transactionRepository.deleteByUid(uid)
        return ResponseEntity.noContent().build()
    }

    // Update transaction
// TODO: deprecated
//    @PutMapping("/{uid}")
//    fun updateTransaction(
//            @RequestBody request: TransactionRequest,
//            @PathVariable("uid") uid: String
//    ): ResponseEntity<Transaction> {
//        val transaction = transactionRepository.findOneByUid(uid)
//        val strategy_id = request.strategyId
//        val strategy = if (strategy_id != null) strategyRepository.findOneByUid(strategy_id) else null
//        if (request.crypto == null && request.stock == null){
//            throw IllegalArgumentException("At least one type of asset must be specified")
//        }
//        val updatedTransaction =
//                transactionRepository.save(
//                        Transaction(
//                                crypto = request.crypto,
//                                stock = request.stock,
//                                quantity = request.quantity,
//                                price = request.price,
//                                type = request.type,
//                                owner = transaction.owner,
//                                strategy = request.strategy,
//                                strategyId = request.strategyId,
//                                status = request.status,
//                                createdDate = transaction.createdDate,
//                                updatedDate = LocalDateTime.now(),
//                                _id = transaction._id,
//                                uid = transaction.uid
//                        )
//                )
//
//        return ResponseEntity.ok(updatedTransaction)
//    }

    // TODO: deprecated
    // Create transaction
//    @PostMapping("/user/{user_id}/{asset_type}/{asset_symbol}")
//    fun createTransaction(
//        @RequestBody request: TransactionRequest,
//        @PathVariable("user_id") user_id: String,
//        @PathVariable("asset_type") asset_type: String,
//        @PathVariable("asset_symbol") asset_symbol: String
//    ): ResponseEntity<Transaction> {
//        val user = userRepository.findOneByUid(user_id)
//        val crypto: Crypto? = if (asset_type == "crypto") cryptoRepository.findOneBySymbol(asset_symbol) else null
//        val stock: Stock? = if (asset_type == "stock") stockRepository.findOneBySymbol(asset_symbol) else null
//
//        if (crypto == null && stock == null) {
//            throw IllegalArgumentException("No asset found for symbol: $asset_symbol")
//        }
//        var maxBuyPrice: BigDecimal? = null
//        var minSellPrice: BigDecimal? = null
//        if (request.maxBuyPrice != null && request.maxBuyPrice != "INF") {
//            maxBuyPrice = BigDecimal(request.maxBuyPrice)
//        } else if (request.maxBuyPrice == "INF") {
//            maxBuyPrice = BigDecimal("10000000") // or any logic you use for "INF"
//        } else {
//            maxBuyPrice = request.price;
//        }
//
//        if (request.minSellPrice != null && request.minSellPrice != "0.00") {
//            minSellPrice = BigDecimal(request.minSellPrice)
//        } else if (request.minSellPrice == "0.00") {
//            minSellPrice = BigDecimal("0.00") // Assuming this is the logic for "0.00"
//        } else {
//            minSellPrice = request.price;
//        }
//
//        // Ensure at least one of them is not null
//        if (maxBuyPrice == null && minSellPrice == null) {
//            throw IllegalArgumentException("Either maxBuyPrice or minSellPrice must be provided.")
//        }
//
//
//        val strategy_id = request.strategyId
//        val strategy = if (strategy_id != null) strategyRepository.findOneByUid(strategy_id) else null
//        if (crypto == null && stock == null){
//            throw IllegalArgumentException("At least one type of asset must be specified")
//        }
//        val transaction =
//                transactionRepository.save(
//                        Transaction(
//                                crypto = crypto,
//                                stock = stock,
//                                price = request.price,
//                                quantity = request.quantity,
//                                maxBuyPrice = maxBuyPrice,
//                                minSellPrice = minSellPrice,
//                                type = request.type,
//                                owner = user,
//                                strategy = strategy,
//                                strategyId = strategy?.uid,
//                                status = request.status
//                        )
//                )
//
//        return ResponseEntity(transaction, HttpStatus.CREATED)
//    }

    // Retrieve all transactions from a user
    @DeleteMapping("/user/{user_id}")
    fun deleteAllTransactionsFromUser(
        @PathVariable("user_id") user_id: String
    ): ResponseEntity<List<Transaction>> {
        val user = userRepository.findOneByUid(user_id)
        val transactions = transactionRepository.findByOwner(user)
        for (transaction in transactions) {
            transactionRepository.deleteByUid(transaction.uid)
        }
        return ResponseEntity.noContent().build()
    }

    // Retrieve all transactions from a crypto
    @DeleteMapping("/crypto/{crypto_id}")
    fun deleteAllTransactionsFromCrypto(
        @PathVariable("crypto_id") crypto_id: String
    ): ResponseEntity<List<Transaction>> {
        val crypto = cryptoRepository.findOneByUuid(crypto_id)
        val transactions = transactionRepository.findByCrypto(crypto)
        for (transaction in transactions) {
            transactionRepository.deleteByUid(transaction.uid)
        }
        return ResponseEntity.noContent().build()
    }
}