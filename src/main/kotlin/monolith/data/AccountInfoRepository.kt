package monolith.data

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface AccountInfoRepository : MongoRepository<AccountInfo, String> {
    fun findOneByUid(uid: String): AccountInfo
    fun findByOwner(owner: User): AccountInfo
    fun deleteByUid(id: String)
    fun deleteByOwner(owner: User)
    override fun deleteAll()
}