package monolith.data

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
// import monolith.data.Crypto

interface CryptoRepository : MongoRepository<Crypto, String> {
    fun findOneByUuid(uuid: String): Crypto
    fun findByUuid(uuid: String): List<Crypto>
    fun findOneByName(name: String): Crypto
    fun findOneBySymbol(symbol: String): Crypto
    fun findBySymbolIn(symbols: List<String>): List<Crypto>
    override fun deleteAll()

}