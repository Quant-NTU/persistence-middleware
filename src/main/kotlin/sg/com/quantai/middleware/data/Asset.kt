package sg.com.quantai.middleware.data

import java.time.LocalDateTime
import org.bson.types.ObjectId
import java.math.BigDecimal

open class Asset(
    open val name: String,
    open val symbol: String,
    open val quantity: BigDecimal,
    open val purchasePrice: BigDecimal,
    val createdDate: LocalDateTime = LocalDateTime.now(),
    val updatedDate: LocalDateTime = LocalDateTime.now(),
)