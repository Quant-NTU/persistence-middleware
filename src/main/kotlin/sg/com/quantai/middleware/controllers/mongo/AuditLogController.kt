package sg.com.quantai.middleware.controllers.mongo

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.data.mongo.AuditLog
import sg.com.quantai.middleware.repositories.mongo.AuditLogRepository

@RestController
@RequestMapping("/audit-logs")
class AuditLogController(
    private val auditLogRepository: AuditLogRepository,
) {
    // GET /audit-logs — returns all entries, newest first
    @GetMapping
    fun getAllAuditLogs(): ResponseEntity<List<AuditLog>> {
        val logs = auditLogRepository.findAll()
            .sortedByDescending { it.timestamp }
        return ResponseEntity.ok(logs)
    }
}
