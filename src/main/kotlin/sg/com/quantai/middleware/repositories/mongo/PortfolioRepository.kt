package sg.com.quantai.middleware.repositories.mongo

import sg.com.quantai.middleware.data.mongo.Portfolio
import sg.com.quantai.middleware.data.mongo.User
import org.springframework.data.mongodb.repository.MongoRepository

interface PortfolioRepository : MongoRepository<Portfolio, String> {
    fun findOneByUid(uid: String): Portfolio
    fun findByOwner(owner: User): List<Portfolio>
    fun findOneByUidAndOwner(uid: String, owner: User): Portfolio
    fun existsByOwnerAndMain(owner: User, isMain: Boolean): Boolean
    fun findByOwnerAndMain(owner: User, isMain: Boolean): Portfolio
    override fun deleteAll()
}