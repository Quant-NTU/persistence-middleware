
package sg.com.quantai.middleware.controllers.mongo

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
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
    private val mongoTemplate: MongoTemplate
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

        // Atomically claim the request so approval and rejection cannot race.
        val claimQuery = Query.query(
            Criteria.where("_id").`is`(pendingRequest.id)
                .and("status").`is`("Pending")
        )

        val claimResult = mongoTemplate.updateFirst(
            claimQuery,
            Update().set("status", "Processing")
                .set("processingStartedAt", now)
                .set("resolvedBy", adminId),
            AccessRequest::class.java
        )

        if (claimResult.modifiedCount != 1L) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Request has already been processed")
        }

        // Only promote a user who is still a non-admin Viewer.
        val promotionQuery = Query.query(
            Criteria.where("uid").`is`(userId)
                .and("role").`is`("Viewer")
                .and("isAdmin").`is`(false)
        )

        try {
            val promotionResult = mongoTemplate.updateFirst(
                promotionQuery,
                Update().set("role", "Trader")
                    .set("updatedDate", now),
                sg.com.quantai.middleware.data.mongo.User::class.java
            )

            if (promotionResult.modifiedCount != 1L) {
                mongoTemplate.updateFirst(
                    Query.query(
                        Criteria.where("_id").`is`(pendingRequest.id)
                            .and("status").`is`("Processing")
                            .and("resolvedBy").`is`(adminId)
                    ),
                    Update().set("status", "Pending")
                        .unset("resolvedBy"),
                    AccessRequest::class.java
                )

                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("User is no longer eligible for Trader access")
            }
        } catch (e: Exception) {
            // Preserve Processing for investigation if the promotion outcome
            // is unknown. Do not risk approving the same request twice.
            throw e
        }

        // Finalize only the request claimed by this Admin.
        val finalizeResult = mongoTemplate.updateFirst(
            Query.query(
                Criteria.where("_id").`is`(pendingRequest.id)
                    .and("status").`is`("Processing")
                    .and("resolvedBy").`is`(adminId)
            ),
            Update().set("status", "Approved")
                .set("resolvedAt", now),
            AccessRequest::class.java
        )

        if (finalizeResult.modifiedCount != 1L) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Trader role granted, but request finalization failed")
        }

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

        // Only one Admin can resolve this Pending request.
        val rejectionQuery = Query.query(
            Criteria.where("_id").`is`(pendingRequest.id)
                .and("status").`is`("Pending")
        )

        val rejectionUpdate = Update()
            .set("status", "Rejected")
            .set("resolvedAt", now)
            .set("resolvedBy", adminId)

        val rejectionResult = mongoTemplate.updateFirst(
            rejectionQuery,
            rejectionUpdate,
            AccessRequest::class.java
        )

        if (rejectionResult.modifiedCount != 1L) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Request has already been processed")
        }

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

        // Atomically demote only a non-admin user who is still a Trader.
        val demotionQuery = Query.query(
            Criteria.where("uid").`is`(userId)
                .and("role").`is`("Trader")
                .and("isAdmin").`is`(false)
        )

        val demotionUpdate = Update()
            .set("role", "Viewer")
            .set("updatedDate", now)

        val demotionResult = mongoTemplate.updateFirst(
            demotionQuery,
            demotionUpdate,
            sg.com.quantai.middleware.data.mongo.User::class.java
        )

        if (demotionResult.modifiedCount != 1L) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("User is no longer eligible for demotion")
        }

        // MongoDB role change takes effect on the next authorized request.

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

    // ============================================================
    // POST /roles/recover
    // Admin reviews stale Trader approval operations
    // ============================================================

    @PostMapping("/recover")
    fun recoverStaleApprovals(
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {

        val admin = try {
            userRepository.findOneByUid(jwt.subject)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Authenticated user not found")
        }

        if (!admin.isAdmin || admin.role != "Admin") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Only Admins can recover access requests")
        }

        val cutoff = LocalDateTime.now().minusMinutes(5)

        val staleQuery = Query.query(
            Criteria.where("status").`is`("Processing")
                .and("processingStartedAt").lt(cutoff)
        )

        val staleRequests = mongoTemplate.find(
            staleQuery,
            AccessRequest::class.java
        )

        val results = mutableListOf<Map<String, String>>()

        for (accessRequest in staleRequests) {
            val target = try {
                userRepository.findOneByUid(accessRequest.userId)
            } catch (e: Exception) {
                null
            }

            if (target != null && !target.isAdmin && target.role == "Viewer") {
                val recoveryQuery = Query.query(
                    Criteria.where("_id").`is`(accessRequest.id)
                        .and("status").`is`("Processing")
                        .and("processingStartedAt")
                        .`is`(accessRequest.processingStartedAt)
                        .and("resolvedBy").`is`(accessRequest.resolvedBy)
                )

                val recoveryUpdate = Update()
                    .set("status", "Pending")
                    .unset("processingStartedAt")
                    .unset("resolvedBy")

                val result = mongoTemplate.updateFirst(
                    recoveryQuery,
                    recoveryUpdate,
                    AccessRequest::class.java
                )

                results.add(
                    mapOf(
                        "userId" to accessRequest.userId,
                        "result" to if (result.modifiedCount == 1L)
                            "RESTORED_TO_PENDING"
                        else
                            "SKIPPED_CONCURRENT_CHANGE"
                    )
                )
            } else {
                results.add(
                    mapOf(
                        "userId" to accessRequest.userId,
                        "result" to "MANUAL_REVIEW_REQUIRED"
                    )
                )
            }
        }

        return ResponseEntity.ok(
            mapOf(
                "staleRequestsFound" to staleRequests.size,
                "results" to results
            )
        )
    }
}
