
package sg.com.quantai.middleware.configs

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.casbin.jcasbin.main.Enforcer
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import sg.com.quantai.middleware.data.mongo.AuditLog
import sg.com.quantai.middleware.repositories.mongo.AuditLogRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository

@Component
class CasbinFilter(
    private val enforcer: Enforcer,
    private val userRepository: UserRepository,
    private val auditLogRepository: AuditLogRepository,
    private val mapper: ObjectMapper
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(CasbinFilter::class.java)
    private val matcher = AntPathMatcher()

    // ============================================================
    // CASBIN ROUTE-TO-PERMISSION MAPPING
    // ============================================================

    private val routeMap = listOf(
        Triple("GET", "/users", "manage_users"),
        Triple("GET", "/users/list", "manage_users"),
        Triple("GET", "/users/count", "manage_users"),
        Triple("GET", "/users/count-session-timeout", "manage_users"),

        Triple("PUT", "/users/admin/**", "manage_users"),
        Triple("DELETE", "/users/admin/**", "manage_users"),

        Triple("GET", "/users/{uid}", "manage_own_profile"),
        Triple("PUT", "/users/{uid}", "manage_own_profile"),
        Triple("DELETE", "/users/{uid}", "manage_own_profile"),

        Triple(
            "POST",
            "/users/update-session-duration",
            "manage_own_profile"
        ),
        Triple(
            "POST",
            "/users/update-session-timeout-status",
            "manage_own_profile"
        ),

        Triple("POST", "/users/enable2FA/**", "manage_own_2fa"),
        Triple("PUT", "/users/disable2FA/**", "manage_own_2fa"),

        Triple("GET", "/portfolios/user/**", "view_portfolio"),
        Triple("GET", "/portfolios/history/**", "view_portfolio"),
        Triple(
            "GET",
            "/portfolios/cash/history/**",
            "view_portfolio"
        ),

        Triple(
            "POST",
            "/portfolios/{user_id}",
            "manage_portfolio"
        ),
        Triple(
            "PUT",
            "/portfolios/{user_id}/{portfolio_id}",
            "manage_portfolio"
        ),
        Triple(
            "DELETE",
            "/portfolios/user/{user_id}/{portfolio_id}",
            "manage_portfolio"
        ),

        Triple(
            "POST",
            "/portfolios/asset/crypto/{portfolio_id}",
            "manage_assets"
        ),
        Triple(
            "POST",
            "/portfolios/asset/stock/{portfolio_id}",
            "manage_assets"
        ),
        Triple(
            "POST",
            "/portfolios/asset/forex/{portfolio_id}",
            "manage_assets"
        ),

        Triple(
            "POST",
            "/portfolios/asset/stock/update/{user_id}",
            "manage_assets"
        ),
        Triple(
            "POST",
            "/portfolios/asset/crypto/update/{user_id}",
            "manage_assets"
        ),
        Triple(
            "POST",
            "/portfolios/asset/forex/update/{user_id}",
            "manage_assets"
        ),

        Triple("POST", "/portfolios/buy/**", "trade_assets"),
        Triple("POST", "/portfolios/sell/**", "trade_assets"),

        Triple(
            "POST",
            "/portfolios/cash/deposit/**",
            "deposit_funds"
        ),
        Triple(
            "POST",
            "/portfolios/cash/withdraw/**",
            "withdraw_funds"
        ),


        // Market data permissions
        Triple("GET", "/forex/transformed", "view_market"),
        Triple("GET", "/forex/transformed/**", "view_market"),
        Triple("GET", "/crypto/transformed", "view_market"),
        Triple("GET", "/crypto/transformed/**", "view_market"),
        Triple("GET", "/stock/transformed", "view_market"),
        Triple("GET", "/stock/transformed/**", "view_market"),
        Triple("GET", "/news_articles/all", "view_market"),

        // Portfolio quotes and benchmark market data
        Triple("GET", "/portfolios/quotes", "view_market"),
        Triple("GET", "/portfolios/quote/{symbol}", "view_market"),
        Triple("GET", "/portfolios/benchmark/{symbol}", "view_market"),
        Triple("GET", "/portfolios/benchmarks/all", "view_market"),


        Triple("GET", "/analytics/**", "view_analytics"),
        Triple("POST", "/analytics/compare", "view_analytics"),

        Triple("GET", "/strategies", "view_strategy"),
        Triple("GET", "/strategies/user/**", "view_strategy"),

        Triple(
            "POST",
            "/strategies/file/{user_id}",
            "create_strategy"
        ),
        Triple("PUT", "/strategies/**", "modify_strategy"),
        Triple(
            "DELETE",
            "/strategies/user/{user_id}/{uid}",
            "delete_strategy"
        ),

        Triple(
            "POST",
            "/test-sandbox/user/{user_id}/{strategy_id}/run",
            "execute_strategy"
        ),

        // RBAC management
        Triple("GET", "/roles/requests", "manage_users"),
        Triple("POST", "/roles/approve", "manage_users"),
        Triple("POST", "/roles/reject", "manage_users"),
	Triple("POST", "/roles/demote", "manage_users"),
        Triple("POST", "/roles/request", "request_role"),

        // Audit log access
        Triple("GET", "/audit-logs", "manage_users"),
        Triple("GET", "/audit-logs/**", "manage_users"),

        // Pipeline permissions
        Triple("GET", "/pipelines", "view_strategy"),
        Triple(
            "GET",
            "/pipelines/user/{user_id}",
            "view_strategy"
        ),
        Triple(
            "GET",
            "/pipelines/user/{user_id}/{pipeline_id}",
            "view_strategy"
        ),
        Triple(
            "POST",
            "/pipelines/{user_id}",
            "create_strategy"
        ),
        Triple(
            "PUT",
            "/pipelines/user/{user_id}/{pipeline_id}",
            "modify_strategy"
        ),
        Triple(
            "DELETE",
            "/pipelines/user/{user_id}/{pipeline_id}",
            "delete_strategy"
        )
    )

    // ============================================================
    // PUBLIC ENDPOINTS
    // ============================================================

    private val publicPaths = setOf(
        "/users",
        "/users/signin",
        "/users/verify-2fa",
        "/users/generate-token",
        "/users/authenticate-token"
    )

    // ============================================================
    // CASBIN AUTHORIZATION FILTER
    // ============================================================

    override fun doFilterInternal(
        req: HttpServletRequest,
        res: HttpServletResponse,
        chain: FilterChain
    ) {

        val uri = req.requestURI.removePrefix(req.contextPath)
        val method = req.method

        // --------------------------------------------------------
        // 1. Allow public authentication endpoints
        // --------------------------------------------------------

        if (
            method == "OPTIONS" ||
            (method == "POST" && uri in publicPaths) ||
            (
                method == "PUT" &&
                matcher.match(
                    "/users/reset-password/{uid}",
                    uri
                )
            )
        ) {
            chain.doFilter(req, res)
            return
        }

        // --------------------------------------------------------
        // 2. Validate JWT authentication
        // --------------------------------------------------------

        val authentication =
            SecurityContextHolder.getContext().authentication
                as? JwtAuthenticationToken

        if (
            authentication == null ||
            !authentication.isAuthenticated
        ) {
            deny(
                res,
                401,
                "A valid access token is required",
                "AUTH_REQUIRED"
            )
            return
        }

        val uid = authentication.token.subject

        if (uid.isNullOrBlank()) {
            deny(
                res,
                401,
                "Invalid token subject",
                "INVALID_SUBJECT"
            )
            return
        }

        // --------------------------------------------------------
        // 3. Retrieve user's current role from MongoDB
        // --------------------------------------------------------

        val user = try {
            userRepository.findOneByUid(uid)
        } catch (e: Exception) {
            deny(
                res,
                401,
                "Authenticated user not found",
                "USER_NOT_FOUND"
            )
            return
        }

        // MongoDB is authoritative for role assignment.
        // An inconsistent Admin flag must never grant Admin access.
        val role =
            if (user.isAdmin && user.role == "Admin") {
                "Admin"
            } else if (
                !user.isAdmin &&
                user.role == "Admin"
            ) {
                "Viewer"
            } else {
                user.role
            }

        // --------------------------------------------------------
        // 4. Resolve API endpoint to Casbin permission
        // --------------------------------------------------------

        val action = routeMap.firstOrNull {
            (verb, pattern, _) ->
            verb == method && matcher.match(pattern, uri)
        }?.third

        if (action == null) {
            log.warn(
                "[CasbinFilter] Unmapped route denied: {} {}",
                method,
                uri
            )

            // Keep the existing fail-closed behaviour.
            deny(
                res,
                403,
                "This endpoint has no authorization policy",
                "ROUTE_NOT_AUTHORIZED"
            )
            return
        }

        // --------------------------------------------------------
        // 5. Evaluate permission using Casbin
        // --------------------------------------------------------

        val allowed = try {
            enforcer.enforce(role, action, "allow")
        } catch (e: Exception) {
            log.error(
                "[CasbinFilter] Permission evaluation failed",
                e
            )
            false
        }

        // --------------------------------------------------------
        // 6. Audit denied permissions only
        // --------------------------------------------------------
        //
        // Successful authorization is not the same as a
        // successful business operation.
        //
        // Therefore:
        // - Log PERMISSION_DENIED for unauthorized attempts.
        // - Do not log routine PERMISSION_GRANTED events.
        // - Business controllers log meaningful successful
        //   events such as TRADER_ACCESS_APPROVED.
        //
        // --------------------------------------------------------

        if (!allowed) {
            try {
                auditLogRepository.save(
                    AuditLog(
                        event = "PERMISSION_DENIED",
                        userId = uid,
                        role = role,
                        action = action,
                        performedBy = uid
                    )
                )
            } catch (e: Exception) {
                log.warn(
                    "[CasbinFilter] Failed to write audit log: {}",
                    e.message
                )
            }

            val upgradeAvailable =
                role == "Viewer" &&
                action in setOf(
                    "manage_portfolio",
                    "manage_assets",
                    "create_strategy",
                    "modify_strategy",
                    "delete_strategy",
                    "execute_strategy",
                    "deposit_funds",
                    "withdraw_funds",
                    "trade_assets"
                )

            deny(
                res,
                403,
                "You do not have permission to: $action",
                "PERMISSION_DENIED",
                mapOf(
                    "role" to role,
                    "action" to action,
                    "upgradeAvailable" to upgradeAvailable
                )
            )

            return
        }

        // --------------------------------------------------------
        // 7. Authorized request proceeds to controller
        // --------------------------------------------------------

        req.setAttribute("authenticatedUid", uid)
        req.setAttribute("userRole", role)

        chain.doFilter(req, res)
    }

    // ============================================================
    // JSON ERROR RESPONSE
    // ============================================================

    private fun deny(
        res: HttpServletResponse,
        status: Int,
        error: String,
        code: String,
        extra: Map<String, Any> = emptyMap()
    ) {
        res.status = status
        res.contentType = MediaType.APPLICATION_JSON_VALUE

        val body = mutableMapOf<String, Any>(
            "error" to error,
            "code" to code
        )

        body.putAll(extra)

        res.writer.write(
            mapper.writeValueAsString(body)
        )
    }
}
