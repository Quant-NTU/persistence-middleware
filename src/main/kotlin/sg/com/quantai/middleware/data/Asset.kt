package sg.com.quantai.middleware.data

import java.time.LocalDateTime
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import java.math.BigDecimal

open class Asset(
    @Id val _id: ObjectId = ObjectId.get(), // document id, it changes when updated via upsert
    @Indexed(unique = true) val uid: String = ObjectId.get().toString(),
    val name: String,
    val symbol: String,
    val quantity: BigDecimal,
    val purchasePrice: BigDecimal,
    val createdDate: LocalDateTime = LocalDateTime.now(),
    val updatedDate: LocalDateTime = LocalDateTime.now(),
)

