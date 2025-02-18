package sg.com.quantai.middleware.repositories.mongo

import sg.com.quantai.middleware.data.mongo.Portfolio
import sg.com.quantai.middleware.data.mongo.User
import org.springframework.data.mongodb.repository.MongoRepository

interface PortfolioRepository : MongoRepository<Portfolio, String> {
    fun findOneByUid(uid: String): Portfolio
    fun findByOwner(owner: User): List<Portfolio>
    fun deleteByUid(uid: String)
    override fun deleteAll()
}