package sg.com.quantai.middleware.repositories.mongo

import org.springframework.data.mongodb.repository.MongoRepository
import sg.com.quantai.middleware.data.mongo.AccessRequest

interface AccessRequestRepository : MongoRepository<AccessRequest, String> {
    fun findByUserId(userId: String): List<AccessRequest>
    fun findByStatus(status: String): List<AccessRequest>
    fun findByUserIdAndStatus(userId: String, status: String): AccessRequest?
}
