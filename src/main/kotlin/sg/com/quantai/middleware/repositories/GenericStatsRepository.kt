package sg.com.quantai.middleware.repositories

import sg.com.quantai.middleware.data.GenericStats
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface GenericStatsRepository : MongoRepository<GenericStats, String> {
    override fun deleteAll()
}