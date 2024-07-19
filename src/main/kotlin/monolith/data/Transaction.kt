package monolith.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DBRef
import java.time.LocalDateTime
import java.math.BigDecimal

@Document(collection = "transactions")
data class Transaction (
        @DBRef
        val crypto: Crypto? = null,

        @DBRef
        val stock: Stock? = null,

        val quantity: BigDecimal = BigDecimal(0.0),

        val price: BigDecimal = BigDecimal(0.0),

        val type: TransactionType = TransactionType.NONE,

        @DBRef
        var strategy: Strategy? = null,

        val strategyId: String? = null,

        val maxBuyPrice: BigDecimal? = null,

        val minSellPrice: BigDecimal? = null,

        val status: TransactionStatus = TransactionStatus.PENDING,

        @DBRef
        val owner: User,

        val createdDate: LocalDateTime = LocalDateTime.now(),

        val updatedDate: LocalDateTime = LocalDateTime.now(),

        @Indexed(unique = true)
        val uid: String = ObjectId.get().toString(),

        @Id
        val _id: ObjectId = ObjectId.get() // document id, it changes when updated via upsert
)