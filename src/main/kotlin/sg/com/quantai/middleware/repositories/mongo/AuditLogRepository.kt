package sg.com.quantai.middleware.repositories.mongo

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import sg.com.quantai.middleware.data.mongo.AuditLog

interface AuditLogRepository : MongoRepository<AuditLog, String> {
    fun findAllByOrderByTimestampDesc(pageable: Pageable): Page<AuditLog>
}
