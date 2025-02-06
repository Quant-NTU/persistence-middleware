package sg.com.quantai.middleware.repositories

import sg.com.quantai.middleware.data.Forex
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface AssetForexRepository : MongoRepository<Forex, String> {
    fun findOneByUid(uid: String): Forex
    fun findByUid(uid: String): Forex
    fun findOneByName(name: String): Forex
    fun findOneBySymbol(symbol: String): Forex?
    fun deleteByUid(uid: String)
    override fun deleteAll()
}