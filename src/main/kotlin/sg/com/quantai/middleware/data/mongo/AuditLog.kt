package sg.com.quantai.middleware.data.mongo

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document(collection = "audit_logs")
data class AuditLog(
    @Id val id: ObjectId = ObjectId.get(),
    val event: String,       // ROLE_ASSIGNED | ROLE_REJECTED | PERMISSION_DENIED
    val userId: String,
    val role: String? = null,
    val action: String? = null,
    val performedBy: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
)