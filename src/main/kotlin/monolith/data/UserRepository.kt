package monolith.data

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface UserRepository : MongoRepository<User, String> {
    fun findOneByUid(uid: String): User
    fun findOneByName(name: String): User
    fun findOneByEmail(email: String): User
    fun findOneByPasswordToken(token: String): User
    fun findAllByIsAdmin(isAdmin: Boolean): List<User>
    fun countByIsAdmin(isAdmin: Boolean): Long
    fun countByHasSessionTimeout(hasSessionTimeout: Boolean): Long
    override fun deleteAll()

}