package sg.com.quantai.middleware.data.mongo

import java.time.LocalDateTime
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient 
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "strategies")
data class Strategy (
    // Database columns
    @Id val _id: ObjectId = ObjectId.get(), // document id, it changes when updated via upsert
    @Indexed(unique = true) val uid: String = ObjectId.get().toString(),
    // Columns
    var title: String,
    var path: String,
    // Timestamps columns
    val createdDate: LocalDateTime = LocalDateTime.now(),
    var updatedDate: LocalDateTime = LocalDateTime.now(),
    // Relationships columns
    @DBRef val owner: User,
) {
    @Transient var content: String? = null
}
