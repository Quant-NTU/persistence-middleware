package monolith.data

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
// import monolith.data.GenericStats

interface GenericStatsRepository : MongoRepository<GenericStats, String> {
    //No function to declare as only 1 instance
    override fun deleteAll()

}