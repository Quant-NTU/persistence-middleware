package sg.com.quantai.middleware.repositories.mongo

import sg.com.quantai.middleware.data.User
import sg.com.quantai.middleware.data.Crypto
import sg.com.quantai.middleware.data.Stock
import sg.com.quantai.middleware.data.Strategy
import sg.com.quantai.middleware.data.Transaction
import sg.com.quantai.middleware.data.enums.TransactionStatus
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.LocalDateTime

interface TransactionRepository : MongoRepository<Transaction, String> {
    fun findOneByUid(uid: String): Transaction
    fun findByOwner(owner: User): List<Transaction>
    fun findByCrypto(crypto: Crypto): List<Transaction>
    fun findByStock(stock: Stock): List<Transaction>
    fun findByStrategy(strategy: Strategy): List<Transaction>
    fun countByStatus(type: TransactionStatus): Long
    fun countByStatusAndCreatedDateBetween(type: TransactionStatus, startDate: LocalDateTime, endDate: LocalDateTime): Long
    fun findByCreatedDateBetween(startDate: LocalDateTime, endDate: LocalDateTime): List<Transaction>
    fun deleteByUid(id: String)
    override fun deleteAll()
}