package sg.com.quantai.middleware.repositories.mongo

import sg.com.quantai.middleware.data.mongo.Pipeline
import sg.com.quantai.middleware.data.mongo.User
import org.springframework.data.mongodb.repository.MongoRepository

interface PipelineRepository : MongoRepository<Pipeline, String> {
    fun findOneByUid(uid: String): Pipeline
    fun findByOwner(owner: User): List<Pipeline>
    fun deleteByUid(uid: String)
    override fun deleteAll()
}