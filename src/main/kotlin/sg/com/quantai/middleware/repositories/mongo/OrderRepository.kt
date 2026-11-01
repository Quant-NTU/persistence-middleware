package sg.com.quantai.middleware.repositories.mongo

import org.springframework.data.mongodb.repository.MongoRepository
import sg.com.quantai.middleware.data.mongo.Order

interface OrderRepository : MongoRepository<Order, String> {
    fun findByUid(uid: String): Order?
    fun findByPortfolioIdOrderByCreatedAtDesc(portfolioId: String): List<Order>
}
