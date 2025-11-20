package sg.com.quantai.middleware.data.mongo

import java.math.BigDecimal
import java.time.LocalDateTime
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DBRef

@Document(collection = "portfolios")
data class Portfolio(
    // Id columns
    @Id val _id: ObjectId = ObjectId.get(), 
    @Indexed(unique = true) val uid: String = ObjectId.get().toString(),
    // Database columns
    val main: Boolean = false,
    val description: String,
    val name: String,
    val cashBalance: BigDecimal = BigDecimal.ZERO,
    // Timestamps columns
    val createdDate: LocalDateTime = LocalDateTime.now(),
    val updatedDate: LocalDateTime = LocalDateTime.now(),
    //Relationships columns
    @DBRef val owner: User
)