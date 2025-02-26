package sg.com.quantai.middleware.repositories.mongo

import org.springframework.data.mongodb.repository.MongoRepository
import sg.com.quantai.middleware.data.mongo.Stock

interface StockRepository : MongoRepository<Stock, String> {
    fun findOneByUid(uid: String): Stock
    fun findByName(name: String): Stock
    fun existsByName(name: String): Boolean
    override fun deleteAll()
}