package sg.com.quantai.middleware.controllers.mongo

import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.data.mongo.AuditLog
import sg.com.quantai.middleware.repositories.mongo.AuditLogRepository

@RestController
@RequestMapping("/audit-logs")
class AuditLogController(
    private val auditLogRepository: AuditLogRepository,
) {
    @GetMapping
    fun getAllAuditLogs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<List<AuditLog>> {
        val pageable = PageRequest.of(page, size)
        val logs = auditLogRepository.findAllByOrderByTimestampDesc(pageable)
        return ResponseEntity.ok(logs.content)
    }
}
