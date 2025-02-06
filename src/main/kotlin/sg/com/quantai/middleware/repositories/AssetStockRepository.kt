package sg.com.quantai.middleware.repositories

import sg.com.quantai.middleware.data.NewStock
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface AssetStockRepository : MongoRepository<NewStock, String> {
    fun findOneByUid(uid: String): NewStock
    fun findByUid(uid: String): List<NewStock>
    fun findOneByName(name: String): NewStock
    fun findOneBySymbol(symbol: String): NewStock?
    fun findBySymbolIn(symbols: List<String>): List<NewStock>
    fun deleteByUid(uid: String)
    override fun deleteAll()
}