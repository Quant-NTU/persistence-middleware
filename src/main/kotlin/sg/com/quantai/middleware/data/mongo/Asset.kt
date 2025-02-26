package sg.com.quantai.middleware.data.mongo

import jakarta.persistence.MappedSuperclass
import java.time.LocalDateTime
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import java.math.BigDecimal

@MappedSuperclass
abstract class Asset(
    // Overridable  columns
    open val name: String?,
    open val quantity: BigDecimal,
    open val purchasePrice: BigDecimal,
    // Id columns
    @Id open val _id: ObjectId = ObjectId.get(),
    @Indexed(unique = true) open val uid: String = ObjectId.get().toString(),
    // Timestamps columns
    open val createdDate: LocalDateTime = LocalDateTime.now(),
    open val updatedDate: LocalDateTime = LocalDateTime.now(),
)