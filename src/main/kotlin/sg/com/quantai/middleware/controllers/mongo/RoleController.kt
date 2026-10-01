package sg.com.quantai.middleware.controllers.mongo

import org.casbin.jcasbin.main.Enforcer
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.data.mongo.AccessRequest
import sg.com.quantai.middleware.data.mongo.AuditLog
import sg.com.quantai.middleware.repositories.mongo.AccessRequestRepository
import sg.com.quantai.middleware.repositories.mongo.AuditLogRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import java.time.LocalDateTime

@RestController
@RequestMapping("/roles")
class RoleController(
    private val userRepository: UserRepository,
    private val accessRequestRepository: AccessRequestRepository,
    private val auditLogRepository: AuditLogRepository,
    private val enforcer: Enforcer,
) {
    // POST /roles/request?userId=xxx
    // Viewer submits Trader access request
    @PostMapping("/request")
    fun requestTraderAccess(@RequestParam userId: String): ResponseEntity<Any> {
        val user = userRepository.findOneByUid(userId)
        if (user.role != "Viewer") {
            return ResponseEntity("Only Viewers can request Trader access", HttpStatus.BAD_REQUEST)
        }
        val existing = accessRequestRepository.findByUserIdAndStatus(userId, "Pending")
        if (existing != null) {
            return ResponseEntity("Request already pending", HttpStatus.CONFLICT)
        }
        val request = accessRequestRepository.save(AccessRequest(userId = userId))
        auditLogRepository.save(AuditLog(
            event = "TRADER_ACCESS_REQUESTED",
            userId = userId,
        ))
        return ResponseEntity(request, HttpStatus.CREATED)
    }

    // GET /roles/requests — Admin views all pending requests
    @GetMapping("/requests")
    fun getPendingRequests(): ResponseEntity<List<AccessRequest>> {
        return ResponseEntity.ok(accessRequestRepository.findByStatus("Pending"))
    }

    // POST /roles/approve?userId=xxx&adminId=yyy
    @PostMapping("/approve")
    fun approveTraderAccess(
        @RequestParam userId: String,
        @RequestParam adminId: String,
    ): ResponseEntity<Any> {
        val user = userRepository.findOneByUid(userId)

        // 1. Update role in MongoDB
        userRepository.save(user.copy(role = "Trader", updatedDate = LocalDateTime.now()))

        // 2. Update Casbin enforcer in memory
        enforcer.getRolesForUser(userId).forEach { enforcer.deleteRoleForUser(userId, it) }
        enforcer.addRoleForUser(userId, "Trader")

        // 3. Update access request record
        val req = accessRequestRepository.findByUserIdAndStatus(userId, "Pending")
        req?.let {
            accessRequestRepository.save(it.copy(
                status = "Approved",
                resolvedAt = LocalDateTime.now(),
                resolvedBy = adminId,
            ))
        }

        // 4. Audit log
        auditLogRepository.save(AuditLog(
            event = "ROLE_ASSIGNED",
            userId = userId,
            role = "Trader",
            performedBy = adminId,
        ))
        return ResponseEntity.ok(mapOf("message" to "Trader role granted to $userId"))
    }

    // POST /roles/reject?userId=xxx&adminId=yyy
    @PostMapping("/reject")
    fun rejectTraderAccess(
        @RequestParam userId: String,
        @RequestParam adminId: String,
    ): ResponseEntity<Any> {
        val req = accessRequestRepository.findByUserIdAndStatus(userId, "Pending")
        req?.let {
            accessRequestRepository.save(it.copy(
                status = "Rejected",
                resolvedAt = LocalDateTime.now(),
                resolvedBy = adminId,
            ))
        }
        auditLogRepository.save(AuditLog(
            event = "ROLE_REJECTED",
            userId = userId,
            performedBy = adminId,
        ))
        return ResponseEntity.ok(mapOf("message" to "Request rejected for $userId"))
    }
}