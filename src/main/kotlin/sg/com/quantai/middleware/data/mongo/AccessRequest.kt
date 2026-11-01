package sg.com.quantai.middleware.data.mongo

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.index.CompoundIndex
import java.time.LocalDateTime

@Document(collection = "access_requests")
@CompoundIndex(
    name = "unique_pending_request_per_user",
    def = "{ 'userId': 1 }",
    unique = true,
    partialFilter = "{ 'status': 'Pending' }"
)
data class AccessRequest(
    @Id val id: ObjectId = ObjectId.get(),
    val userId: String,
    val requestedRole: String = "Trader",
    val status: String = "Pending",  // Pending | Approved | Rejected
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val processingStartedAt: LocalDateTime? = null,
    val resolvedAt: LocalDateTime? = null,
    val resolvedBy: String? = null,  // admin uid
)