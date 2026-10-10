
package sg.com.quantai.middleware.controllers.mongo

import java.time.LocalDateTime
import org.casbin.jcasbin.main.Enforcer
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.data.mongo.AccessRequest
import sg.com.quantai.middleware.data.mongo.AuditLog
import sg.com.quantai.middleware.repositories.mongo.AccessRequestRepository
import sg.com.quantai.middleware.repositories.mongo.AuditLogRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository

@RestController
@RequestMapping("/roles")
class RoleController(
    private val userRepository: UserRepository,
    private val accessRequestRepository: AccessRequestRepository,
    private val auditLogRepository: AuditLogRepository,
    private val enforcer: Enforcer
) {

    // POST /roles/request
    // Authenticated Viewer submits a Trader access request
    @PostMapping("/request")
    fun requestTraderAccess(
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val userId = jwt.subject

        val user = try {
            userRepository.findOneByUid(userId)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Authenticated user not found")
        }

        if (user.isAdmin || user.role != "Viewer") {
            return ResponseEntity.badRequest()
                .body("Only Viewers can request Trader access")
        }

        val existing = accessRequestRepository.findByUserIdAndStatus(
            userId,
            "Pending"
        )

        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Request already pending")
        }

        val request = accessRequestRepository.save(
            AccessRequest(userId = userId)
        )

        auditLogRepository.save(
            AuditLog(
                event = "TRADER_ACCESS_REQUESTED",
                userId = userId
            )
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(request)
    }

    // GET /roles/requests
    // Admin views all pending Trader access requests
    @GetMapping("/requests")
    fun getPendingRequests(
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val adminId = jwt.subject

        val admin = try {
            userRepository.findOneByUid(adminId)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Authenticated user not found")
        }

        if (!admin.isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Only Admins can view access requests")
        }

        val requests = accessRequestRepository.findByStatus("Pending")

        return ResponseEntity.ok(requests)
    }

    // POST /roles/approve?userId=xxx
    // Admin approves an existing Trader access request
    @PostMapping("/approve")
    fun approveTraderAccess(
        @RequestParam userId: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val adminId = jwt.subject

        val admin = try {
            userRepository.findOneByUid(adminId)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Authenticated user not found")
        }

        if (!admin.isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Only Admins can approve Trader access")
        }

        if (adminId == userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("You cannot approve your own access request")
        }

        val pendingRequest = accessRequestRepository.findByUserIdAndStatus(
            userId,
            "Pending"
        ) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("No pending Trader access request found")

        val user = try {
            userRepository.findOneByUid(userId)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Requested user not found")
        }

        if (user.isAdmin || user.role != "Viewer") {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("User is no longer eligible for Trader access")
        }

        val now = LocalDateTime.now()

        // 1. Update user's role in MongoDB
        userRepository.save(
            user.copy(
                role = "Trader",
                updatedDate = now
            )
        )

        // 2. Update Casbin role assignments
        enforcer.getRolesForUser(userId).forEach { existingRole ->
            enforcer.deleteRoleForUser(userId, existingRole)
        }

        enforcer.addRoleForUser(userId, "Trader")

        // 3. Mark the pending request as approved
        accessRequestRepository.save(
            pendingRequest.copy(
                status = "Approved",
                resolvedAt = now,
                resolvedBy = adminId
            )
        )

        // 4. Record the authenticated Admin in the audit log
        auditLogRepository.save(
            AuditLog(
                event = "ROLE_ASSIGNED",
                userId = userId,
                role = "Trader",
                performedBy = adminId
            )
        )

        return ResponseEntity.ok(
            mapOf("message" to "Trader role granted to $userId")
        )
    }

    // POST /roles/reject?userId=xxx
    // Admin rejects an existing Trader access request
    @PostMapping("/reject")
    fun rejectTraderAccess(
        @RequestParam userId: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val adminId = jwt.subject

        val admin = try {
            userRepository.findOneByUid(adminId)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Authenticated user not found")
        }

        if (!admin.isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Only Admins can reject Trader access")
        }

        if (adminId == userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("You cannot reject your own access request")
        }

        val pendingRequest = accessRequestRepository.findByUserIdAndStatus(
            userId,
            "Pending"
        ) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("No pending Trader access request found")

        val now = LocalDateTime.now()

        accessRequestRepository.save(
            pendingRequest.copy(
                status = "Rejected",
                resolvedAt = now,
                resolvedBy = adminId
            )
        )

        auditLogRepository.save(
            AuditLog(
                event = "ROLE_REJECTED",
                userId = userId,
                performedBy = adminId
            )
        )

        return ResponseEntity.ok(
            mapOf("message" to "Request rejected for $userId")
        )
    }
}