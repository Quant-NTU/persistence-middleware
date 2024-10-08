package sg.com.quantai.middleware.repositories

import sg.com.quantai.middleware.data.AccountInfo
import sg.com.quantai.middleware.data.User
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface AccountInfoRepository : MongoRepository<AccountInfo, String> {
    fun findOneByUid(uid: String): AccountInfo
    fun findByOwner(owner: User): AccountInfo
    fun deleteByUid(id: String)
    fun deleteByOwner(owner: User)
    override fun deleteAll()
}