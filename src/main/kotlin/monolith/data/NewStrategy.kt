package monolith.data
import monolith.data.Transaction
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference
import org.springframework.data.mongodb.core.mapping.DBRef
import java.time.LocalDateTime
import java.math.BigDecimal
import kotlin.collections.List

@Document(collection = "newstrategies")
data class NewStrategy (
    // Database columns
    @Id val _id: ObjectId = ObjectId.get(), // document id, it changes when updated via upsert
    @Indexed(unique = true) val uid: String = ObjectId.get().toString(),
    // Columns columns
    val title: String,
    val path: String,
    // Timestamps columns
    val createdDate: LocalDateTime = LocalDateTime.now(),
    val updatedDate: LocalDateTime = LocalDateTime.now(),
    //Relationships columns
    @DBRef val owner: User
)