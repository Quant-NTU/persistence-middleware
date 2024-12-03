package sg.com.quantai.middleware.repositories.mongo

import sg.com.quantai.middleware.data.User
import sg.com.quantai.middleware.data.Strategy
import sg.com.quantai.middleware.data.enums.StrategyStatus

import org.springframework.data.mongodb.repository.MongoRepository

interface StrategyRepository : MongoRepository<Strategy, String> {
    fun findOneByUid(uid: String): Strategy?
    fun countByStatus(status: StrategyStatus): Long
    fun findByOwner(owner: User): List<Strategy>
    fun deleteByUid(uid: String)
    override fun deleteAll()
}