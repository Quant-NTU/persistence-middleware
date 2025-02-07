package sg.com.quantai.middleware.repositories

import sg.com.quantai.middleware.data.NewStock
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface AssetStockRepository : MongoRepository<NewStock, String> {
    fun findByUid(uid: String): NewStock
    fun findByName(name: String): List<NewStock>
    fun findBySymbol(symbol: String): List<NewStock>
    fun deleteByUid(uid: String)
    override fun deleteAll()
}