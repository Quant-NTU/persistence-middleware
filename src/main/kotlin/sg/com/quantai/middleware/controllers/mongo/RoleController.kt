
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

    // ============================================================
    // POST /roles/request
    // Viewer requests Trader access
    // ============================================================

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
                userId = userId,
                role = "Viewer",
                action = "request_trader_access",
                performedBy = userId
            )
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(request)
    }

    // ============================================================
    // GET /roles/requests
    // Admin retrieves pending Trader access requests
    // ============================================================

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

        if (!admin.isAdmin || admin.role != "Admin") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Only Admins can view access requests")
        }

        return ResponseEntity.ok(
            accessRequestRepository.findByStatus("Pending")
        )
    }

    // ============================================================
    // POST /roles/approve?userId=xxx
    // Admin approves Trader access
    // ============================================================

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

        if (!admin.isAdmin || admin.role != "Admin") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Only Admins can approve Trader access")
        }

        if (adminId == userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("You cannot approve your own access request")
        }

        val pendingRequest =
            accessRequestRepository.findByUserIdAndStatus(
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

        // 1. Update user role in MongoDB
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

        // 3. Mark access request as approved
        accessRequestRepository.save(
            pendingRequest.copy(
                status = "Approved",
                resolvedAt = now,
                resolvedBy = adminId
            )
        )

        // 4. Record successful approval
        auditLogRepository.save(
            AuditLog(
                event = "TRADER_ACCESS_APPROVED",
                userId = userId,
                role = "Trader",
                action = "approve_trader_access",
                performedBy = adminId
            )
        )

        return ResponseEntity.ok(
            mapOf(
                "message" to "Trader role granted to $userId"
            )
        )
    }

    // ============================================================
    // POST /roles/reject?userId=xxx
    // Admin rejects Trader access
    // ============================================================

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

        if (!admin.isAdmin || admin.role != "Admin") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Only Admins can reject Trader access")
        }

        if (adminId == userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("You cannot reject your own access request")
        }

        val pendingRequest =
            accessRequestRepository.findByUserIdAndStatus(
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
                event = "TRADER_ACCESS_REJECTED",
                userId = userId,
                role = "Viewer",
                action = "reject_trader_access",
                performedBy = adminId
            )
        )

        return ResponseEntity.ok(
            mapOf(
                "message" to "Request rejected for $userId"
            )
        )
    }

    // ============================================================
    // POST /roles/demote?userId=xxx
    // Admin revokes Trader privileges
    // ============================================================

    @PostMapping("/demote")
    fun demoteTraderToViewer(
        @RequestParam userId: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {

        val adminId = jwt.subject

        // 1. Verify requester is an Admin
        val admin = try {
            userRepository.findOneByUid(adminId)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Authenticated user not found")
        }

        if (!admin.isAdmin || admin.role != "Admin") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Only Admins can demote Traders")
        }

        // 2. Prevent self-demotion
        if (adminId == userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("You cannot demote your own account")
        }

        // 3. Find the target user
        val user = try {
            userRepository.findOneByUid(userId)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Target user not found")
        }

        // 4. Only Traders can be demoted to Viewer
        if (user.isAdmin || user.role != "Trader") {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Only Trader accounts can be demoted to Viewer")
        }

        val now = LocalDateTime.now()

        // 5. Update role in MongoDB
        userRepository.save(
            user.copy(
                role = "Viewer",
                updatedDate = now
            )
        )

        // 6. Synchronize Casbin role assignment
        enforcer.getRolesForUser(userId).forEach { existingRole ->
            enforcer.deleteRoleForUser(userId, existingRole)
        }

        enforcer.addRoleForUser(userId, "Viewer")

        // 7. Record Trader demotion
        auditLogRepository.save(
            AuditLog(
                event = "TRADER_DEMOTED",
                userId = userId,
                role = "Viewer",
                action = "demote_trader_to_viewer",
                performedBy = adminId
            )
        )

        return ResponseEntity.ok(
            mapOf(
                "message" to "Trader $userId successfully demoted to Viewer",
                "userId" to userId,
                "previousRole" to "Trader",
                "newRole" to "Viewer",
                "performedBy" to adminId
            )
        )
    }
}
