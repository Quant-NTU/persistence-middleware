package monolith.data

import monolith.services.POJOS.CoinDetails.LinkObj
import monolith.services.POJOS.CoinHistory.CoinHistoryInstance
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.LocalDateTime

@Document(collection = "stocks")
data class Stock(
        val name: String,

        val symbol: String,

        val marketCap: BigDecimal,

        val price: BigDecimal,

        val updatedDate: LocalDateTime = LocalDateTime.now(),

        val description: String = "None",

        val change: BigDecimal,

        val volume: String,

        @DBRef(lazy=true)
        val transactions: List<Transaction> = emptyList<Transaction>(),

        @Indexed(unique = true)
        val uuid: String = ObjectId.get().toString(),

        @Id
        val _id: ObjectId = ObjectId.get() // document id, it changes when updated via upsert
)
