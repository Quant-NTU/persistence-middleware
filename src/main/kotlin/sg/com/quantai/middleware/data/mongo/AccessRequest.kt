package sg.com.quantai.middleware.data.mongo

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document(collection = "access_requests")
data class AccessRequest(
    @Id val id: ObjectId = ObjectId.get(),
    val userId: String,
    val requestedRole: String = "Trader",
    val status: String = "Pending",  // Pending | Approved | Rejected
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val resolvedAt: LocalDateTime? = null,
    val resolvedBy: String? = null,  // admin uid
)