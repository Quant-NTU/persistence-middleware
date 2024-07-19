package monolith.data

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