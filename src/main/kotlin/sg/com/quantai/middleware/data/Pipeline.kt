package sg.com.quantai.middleware.data

import java.time.LocalDateTime
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "pipelines")
data class Pipeline (
    // Database columns
    @Id val _id: ObjectId = ObjectId.get(), // Pipeline id, it changes when updated via upsert
    @Indexed(unique = true) val uid: String = ObjectId.get().toString(),
    // Id columns
    val title: String,
    // Timestamps columns
    val createdDate: LocalDateTime = LocalDateTime.now(),
    val updatedDate: LocalDateTime = LocalDateTime.now(),
    //Relationships columns
    @DBRef val owner: User,
    // @DBRef val portfolio: Portfolio,
    @DBRef(lazy=true) val strategies: List<Strategy>? = emptyList<Strategy>(),
    val portfolio: String? = "Temp String",
    val description: String? = null,
) 