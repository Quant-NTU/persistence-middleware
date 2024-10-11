package sg.com.quantai.middleware.repositories

import sg.com.quantai.middleware.data.Pipeline
import sg.com.quantai.middleware.data.User
import org.springframework.data.mongodb.repository.MongoRepository
import org.bson.types.ObjectId

interface PipelineRepository : MongoRepository<Pipeline, String> {
    fun findOneByUid(uid: String): Pipeline
    fun findByOwner(owner: User): List<Pipeline>
    fun deleteByUid(uid: String)
    override fun deleteAll()
}