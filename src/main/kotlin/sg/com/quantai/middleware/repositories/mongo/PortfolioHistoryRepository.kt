package sg.com.quantai.middleware.repositories.mongo

import sg.com.quantai.middleware.data.mongo.Portfolio
import sg.com.quantai.middleware.data.mongo.User
import org.springframework.data.mongodb.repository.MongoRepository
import sg.com.quantai.middleware.data.mongo.PortfolioHistory
import java.math.BigDecimal

interface PortfolioHistoryRepository : MongoRepository<PortfolioHistory, String> {
    fun findOneByUid(uid: String): PortfolioHistory
    override fun deleteAll()
    fun quantity(quantity: BigDecimal): MutableList<PortfolioHistory>
    fun findByPortfolio(portfolio_id:String):List<PortfolioHistory>
}