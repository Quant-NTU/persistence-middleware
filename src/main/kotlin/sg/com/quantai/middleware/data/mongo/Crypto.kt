package sg.com.quantai.middleware.data.mongo

import org.bson.types.ObjectId
import org.springframework.data.annotation.TypeAlias
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.LocalDateTime

@Document(collection = "cryptos")
@TypeAlias("crypto")
class Crypto (
    name: String?,
    quantity: BigDecimal,
    purchasePrice: BigDecimal,

    // Pass along parent fields to constructor with defaults
    _id: ObjectId = ObjectId.get(),
    uid: String = ObjectId.get().toString(),
    createdDate: LocalDateTime = LocalDateTime.now(),
    updatedDate: LocalDateTime = LocalDateTime.now(),

    // Custom  columns
    val symbol: String?,
) : Asset(name, quantity, purchasePrice, _id, uid, createdDate, updatedDate)