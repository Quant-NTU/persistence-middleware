package sg.com.quantai.middleware.repositories.mongo

import org.springframework.data.mongodb.repository.MongoRepository
import sg.com.quantai.middleware.data.mongo.Forex

interface ForexRepository : MongoRepository<Forex, String> {
    fun findOneByUid(uid: String): Forex
    fun findByName(name: String): Forex
    fun existsByName(name: String): Boolean
    override fun deleteAll()
}