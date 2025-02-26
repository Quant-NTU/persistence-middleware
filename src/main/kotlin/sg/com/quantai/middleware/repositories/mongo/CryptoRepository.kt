package sg.com.quantai.middleware.repositories.mongo

import sg.com.quantai.middleware.data.mongo.Portfolio
import sg.com.quantai.middleware.data.mongo.User
import org.springframework.data.mongodb.repository.MongoRepository
import sg.com.quantai.middleware.data.mongo.Crypto

interface CryptoRepository : MongoRepository<Crypto, String> {
    fun findOneByUid(uid: String): Crypto
    fun findByName(name: String): Crypto
    fun existsByName(name: String): Boolean
    override fun deleteAll()
    fun name(name: String): MutableList<Crypto>
}