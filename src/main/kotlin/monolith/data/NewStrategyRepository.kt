package monolith.data

import org.springframework.data.mongodb.repository.MongoRepository

interface NewStrategyRepository : MongoRepository<NewStrategy, String> {
    fun findOneByUid(uid: String): NewStrategy?
    fun findByOwner(owner: User): List<NewStrategy>
    fun deleteByUid(uid: String)
    override fun deleteAll()
}