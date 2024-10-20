package sg.com.quantai.middleware.data

import java.time.LocalDateTime
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient 
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "newstrategies")
data class NewStrategy(
    // Database columns
    @Id val _id: ObjectId = ObjectId.get(), // document id, it changes when updated via upsert
    @Indexed(unique = true) val uid: String = ObjectId.get().toString(),

    // Columns columns
    var title: String,  // Changed from val to var
    var path: String,   // Changed from val to var

    // Timestamps columns
    val createdDate: LocalDateTime = LocalDateTime.now(),
    var updatedDate: LocalDateTime = LocalDateTime.now(),  // Changed from val to var

    // Relationships columns
    @DBRef val owner: User,
) {
    @Transient var content: String? = null
}
