package sg.com.quantai.middleware.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DBRef
import java.time.LocalDateTime
import java.math.BigDecimal
import sg.com.quantai.middleware.services.POJOS.CoinDetails.LinkObj
import sg.com.quantai.middleware.services.POJOS.CoinHistory.CoinHistoryInstance

//TODO: Populate from data incoming from the Crypt API webservice
//TODO: We can also save this data into a static file - which should assist our demo presentations
@Document(collection = "cryptos")
data class Crypto (
        val name: String,

        val symbol: String,

        val marketCap: BigDecimal,

        val price: BigDecimal,

        val updatedDate: LocalDateTime = LocalDateTime.now(),

        val description: String = "None",

        val iconurl: String,

        val change: BigDecimal, 

        val rank: Int, 

        val volume: String,

        val allTimeHighPrice: BigDecimal? = null,

        val numberOfMarkets: Int? = null, 

        val numberOfExchanges: Int? = null,

        val approvedSupply: Boolean? = null, 

        val totalSupply: String? = null, 

        val circulatingSupply: String? = null,

        val links: List<LinkObj> =  emptyList<LinkObj>(),

        val coinHistory: CoinHistoryInstance? = null,

        @DBRef(lazy=true)
        val transactions: List<Transaction> = emptyList<Transaction>(),

        @Indexed(unique = true)
        val uuid: String,

        @Id
        val _id: ObjectId = ObjectId.get() // document id, it changes when updated via upsert
)