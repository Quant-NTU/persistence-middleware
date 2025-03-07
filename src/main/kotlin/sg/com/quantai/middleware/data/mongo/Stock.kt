package sg.com.quantai.middleware.data.mongo

import org.bson.types.ObjectId
import org.springframework.data.annotation.TypeAlias
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.LocalDateTime

@Document(collection = "stocks")
@TypeAlias("stock")
data class Stock (
    override val name: String?,
    override val quantity: BigDecimal,
    override val purchasePrice: BigDecimal,

    // Pass along parent fields to constructor with defaults
    override val _id: ObjectId = ObjectId.get(),
    override val uid: String = ObjectId.get().toString(),
    override val createdDate: LocalDateTime = LocalDateTime.now(),
    override val updatedDate: LocalDateTime = LocalDateTime.now(),

    // Custom  columns
    val ticker: String?
) : Asset(name, quantity, purchasePrice, _id, uid, createdDate, updatedDate)