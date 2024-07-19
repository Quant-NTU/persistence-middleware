package monolith.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document(collection = "stats")
data class GenericStats(
        val total: Int,

        val totalCoins: Int,

        val totalMarkets: Int,

        val totalExchanges: Int,

        val totalMarketCap: String,

        val total24hVolume: String,

        val updatedDate: LocalDateTime = LocalDateTime.now(),

        @DBRef(lazy = true) 
        val coins: List<Crypto> = emptyList<Crypto>(),

        @Indexed(unique = true) 
        val uid: String = ObjectId.get().toString(),

        @Id 
        val _id: ObjectId = ObjectId.get() // document id, it changes when updated via upsert
)
