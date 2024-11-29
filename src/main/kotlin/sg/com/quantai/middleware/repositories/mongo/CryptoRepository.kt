package sg.com.quantai.middleware.repositories.mongo

import sg.com.quantai.middleware.data.Crypto
import org.springframework.data.mongodb.repository.MongoRepository

interface CryptoRepository : MongoRepository<Crypto, String> {
    fun findOneByUuid(uuid: String): Crypto
    fun findByUuid(uuid: String): List<Crypto>
    fun findOneByName(name: String): Crypto
    fun findOneBySymbol(symbol: String): Crypto
    fun findBySymbolIn(symbols: List<String>): List<Crypto>
    override fun deleteAll()

}