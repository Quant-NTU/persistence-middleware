package sg.com.quantai.middleware.repositories

import sg.com.quantai.middleware.data.NewCrypto
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface AssetCryptoRepository : MongoRepository<NewCrypto, String> {
    fun findByUid(uid: String): NewCrypto
    fun findByName(name: String): List<NewCrypto>
    fun findBySymbol(symbol: String): List<NewCrypto>
    fun deleteByUid(uid: String)
    override fun deleteAll()
}