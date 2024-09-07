package sg.com.quantai.middleware.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DBRef
import java.time.LocalDateTime
import java.math.BigDecimal


//TODO: Populate from data incoming from the Crypt API webservice
//TODO: We can also save this data into a static file - which should assist our demo presentations
@Document(collection = "portfolios")
data class Portfolio (
        val symbol: String,

        val name: String,

        val quantity: BigDecimal = BigDecimal(0.0),      

        val price: BigDecimal = BigDecimal(0.0),

        val platform: String,
        
        @DBRef
        val owner: User,

        @Indexed(unique = true)
        val uid: String = ObjectId.get().toString(),

        @Id
        val _id: ObjectId = ObjectId.get() // document id, it changes when updated via upsert
)