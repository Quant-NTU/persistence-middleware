
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

    private val log =
        LoggerFactory.getLogger(CasbinFilter::class.java)

    private val pathMatcher = AntPathMatcher()

    // ============================================================
    // ROUTE TO PERMISSION MAPPINGS
    // ============================================================

    private val routeMap = listOf(

        // --------------------------------------------------------
        // USER ADMINISTRATION
        // --------------------------------------------------------

        Triple("GET", "/users/list", "manage_users"),
        Triple("PUT", "/users/admin/**", "manage_users"),
        Triple("DELETE", "/users/admin/**", "manage_users"),

        // --------------------------------------------------------
        // PORTFOLIO VIEWING
        // --------------------------------------------------------

        Triple("GET", "/portfolios/user/**", "view_portfolio"),
        Triple("GET", "/portfolios/history/**", "view_portfolio"),
        Triple("GET", "/portfolios/cash/history/**", "view_portfolio"),

        // --------------------------------------------------------
        // PORTFOLIO MANAGEMENT
        // --------------------------------------------------------

        Triple("POST", "/portfolios/{user_id}", "manage_portfolio"),
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

        // --------------------------------------------------------
        // MANUAL ASSET MANAGEMENT
        // --------------------------------------------------------

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

        // --------------------------------------------------------
        // TRADING
        // --------------------------------------------------------

        Triple("POST", "/portfolios/buy/**", "trade_assets"),
        Triple("POST", "/portfolios/sell/**", "trade_assets"),

        // --------------------------------------------------------
        // SIMULATED CASH MANAGEMENT
        // --------------------------------------------------------

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

        // --------------------------------------------------------
        // MARKET DATA
        // --------------------------------------------------------

        Triple("GET", "/forex/transformed", "view_market"),
        Triple("GET", "/forex/transformed/**", "view_market"),

        Triple("GET", "/crypto/transformed", "view_market"),
        Triple("GET", "/crypto/transformed/**", "view_market"),

        Triple("GET", "/stock/transformed", "view_market"),
        Triple("GET", "/stock/transformed/**", "view_market"),

        // --------------------------------------------------------
        // NEWS
        // --------------------------------------------------------

        Triple("GET", "/news_articles/all", "view_market"),

        // --------------------------------------------------------
        // ANALYTICS
        // --------------------------------------------------------

        Triple("GET", "/analytics/**", "view_analytics"),
        Triple("POST", "/analytics/compare", "view_analytics"),

        // --------------------------------------------------------
        // STRATEGY VIEWING
        // --------------------------------------------------------

        Triple("GET", "/strategies", "view_strategy"),
        Triple("GET", "/strategies/user/**", "view_strategy"),

        // --------------------------------------------------------
        // STRATEGY CREATION AND UPDATES
        // --------------------------------------------------------

        Triple(
            "POST",
            "/strategies/file/{user_id}",
            "create_strategy"
        ),

        // Reserved for a future separate PUT strategy endpoint.
        Triple("PUT", "/strategies/**", "modify_strategy"),

        Triple(
            "DELETE",
            "/strategies/user/{user_id}/{uid}",
            "delete_strategy"
        ),

        // --------------------------------------------------------
        // SANDBOX EXECUTION
        // --------------------------------------------------------

        Triple(
            "POST",
            "/test-sandbox/user/{user_id}/{strategy_id}/run",
            "execute_strategy"
        ),

        // --------------------------------------------------------
        // ROLE MANAGEMENT
        // --------------------------------------------------------

        Triple("GET", "/roles/requests", "manage_users"),
        Triple("POST", "/roles/approve", "manage_users"),
        Triple("POST", "/roles/reject", "manage_users"),
        Triple("POST", "/roles/request", "request_role"),

        // --------------------------------------------------------
        // AUDIT LOGS
        // --------------------------------------------------------

        Triple("GET", "/audit-logs", "manage_users"),
        Triple("GET", "/audit-logs/**", "manage_users"),

        // --------------------------------------------------------
        // TWO-FACTOR AUTHENTICATION
        // --------------------------------------------------------

        Triple(
            "POST",
            "/users/enable2FA/**",
            "manage_own_2fa"
        ),
        Triple(
            "PUT",
            "/users/disable2FA/**",
            "manage_own_2fa"
        )
    )

    // These endpoints are handled by the authentication flow.
    // Their internal security must be reviewed independently.
    private val publicPaths = setOf(
	"/users",
        "/users/signin",
        "/users/verify-2fa",
        "/users/generate-token",
        "/users/authenticate-token"
    )

    override fun doFilterInternal(
        req: HttpServletRequest,
        res: HttpServletResponse,
        chain: FilterChain
    ) {
        val uri = req.requestURI.removePrefix(req.contextPath)
        val method = req.method

        // Allow CORS preflight requests.
        if (method == "OPTIONS") {
            chain.doFilter(req, res)
            return
        }

        // Public authentication endpoints.
        if (method == "POST" && uri in publicPaths) {
            chain.doFilter(req, res)
            return
        }

        // ========================================================
        // 1. VERIFY JWT AUTHENTICATION
        // ========================================================

        val authentication =
            SecurityContextHolder.getContext().authentication

        val jwtAuthentication =
            authentication as? JwtAuthenticationToken

        if (
            jwtAuthentication == null ||
            !jwtAuthentication.isAuthenticated
        ) {
            deny(
                res,
                401,
                "A valid access token is required",
                "AUTH_REQUIRED"
            )
            return
        }

        val uid = jwtAuthentication.token.subject

        if (uid.isNullOrBlank()) {
            deny(
                res,
                401,
                "Invalid token subject",
                "INVALID_SUBJECT"
            )
            return
        }

        // ========================================================
        // 2. RESOLVE USER AND ROLE FROM DATABASE
        // ========================================================

        val user = try {
            userRepository.findOneByUid(uid)
        } catch (e: Exception) {
            log.warn(
                "[CasbinFilter] Unable to resolve user: {}",
                e.message
            )

            deny(
                res,
                401,
                "Authenticated user not found",
                "USER_NOT_FOUND"
            )
            return
        }

        val role = if (user.isAdmin) {
            "Admin"
        } else {
            user.role
        }

        // ========================================================
        // 3. MATCH REQUEST TO REQUIRED PERMISSION
        // ========================================================

        val action = routeMap.firstOrNull {
            (mappedMethod, pattern, _) ->
            mappedMethod == method &&
                pathMatcher.match(pattern, uri)
        }?.third

        if (action == null) {
            log.warn(
                "[CasbinFilter] Unmapped route denied: {} {}",
                method,
                uri
            )

            deny(
                res,
                403,
                "This endpoint has no authorization policy",
                "ROUTE_NOT_AUTHORIZED"
            )
            return
        }

        // ========================================================
        // 4. EVALUATE CASBIN POLICY
        // ========================================================

        val allowed = try {
            enforcer.enforce(role, action, "allow")
        } catch (e: Exception) {
            log.error(
                "[CasbinFilter] Permission evaluation failed",
                e
            )
            false
        }

        // ========================================================
        // 5. RECORD AUDIT LOG
        // ========================================================

        try {
            auditLogRepository.save(
                AuditLog(
                    event = if (allowed) {
                        "PERMISSION_GRANTED"
                    } else {
                        "PERMISSION_DENIED"
                    },
                    userId = uid,
                    role = role,
                    action = action
                )
            )
        } catch (e: Exception) {
            log.warn(
                "[CasbinFilter] Failed to write audit log: {}",
                e.message
            )
        }

        // ========================================================
        // 6. REJECT UNAUTHORIZED REQUESTS
        // ========================================================

        if (!allowed) {
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

        // ========================================================
        // 7. PASS AUTHENTICATED IDENTITY TO CONTROLLER
        // ========================================================

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
