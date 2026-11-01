package sg.com.quantai.middleware.data.mongo

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import sg.com.quantai.middleware.data.mongo.enums.AssetType
import sg.com.quantai.middleware.data.mongo.enums.OrderSide
import sg.com.quantai.middleware.data.mongo.enums.OrderStatus
import sg.com.quantai.middleware.data.mongo.enums.OrderType
import java.math.BigDecimal
import java.time.LocalDateTime

@Document(collection = "orders")
data class Order(
    // Id columns
    @Id val _id: ObjectId = ObjectId.get(),
    @Indexed(unique = true) val uid: String = ObjectId.get().toString(),

    // Who / where
    val userId: String,
    val portfolioId: String,

    // What
    val symbol: String,
    val assetType: AssetType,
    val name: String? = null,
    val side: OrderSide,
    val quantity: BigDecimal,
    val orderType: OrderType,
    val limitPrice: BigDecimal? = null,
    val referencePrice: BigDecimal? = null,

    // Lifecycle
    val status: OrderStatus = OrderStatus.PENDING,
    val filledQty: BigDecimal = BigDecimal.ZERO,
    val avgPx: BigDecimal? = null,
    val processedExecIds: MutableSet<String> = mutableSetOf(),
    val rejectReason: String? = null,

    // Timestamps
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
