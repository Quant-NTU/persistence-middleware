package sg.com.quantai.middleware.repositories

import sg.com.quantai.middleware.data.Forex
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface AssetForexRepository : MongoRepository<Forex, String> {
    fun findByUid(uid: String): Forex
    fun findByName(name: String): List<NewForex>
    fun findBySymbol(symbol: String): List<NewForex>
    fun deleteByUid(uid: String)
    override fun deleteAll()
}