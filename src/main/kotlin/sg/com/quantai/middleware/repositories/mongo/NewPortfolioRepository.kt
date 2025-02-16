package sg.com.quantai.middleware.repositories

import sg.com.quantai.middleware.data.NewPortfolio
import sg.com.quantai.middleware.data.mongo.User
import org.springframework.data.mongodb.repository.MongoRepository
import org.bson.types.ObjectId

interface NewPortfolioRepository : MongoRepository<NewPortfolio, String> {
    fun findOneByUid(uid: String): NewPortfolio
    fun findByOwner(owner: User): List<NewPortfolio>
    fun deleteByUid(uid: String)
    override fun deleteAll()
}