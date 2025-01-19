package sg.com.quantai.middleware.repositories.mongo

import sg.com.quantai.middleware.data.GenericStats
import org.springframework.data.mongodb.repository.MongoRepository

interface GenericStatsRepository : MongoRepository<GenericStats, String> {
    override fun deleteAll()
}