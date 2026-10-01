package sg.com.quantai.middleware.configs

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.casbin.jcasbin.main.Enforcer
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
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
    private val mapper: ObjectMapper,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(CasbinFilter::class.java)
    private val pathMatcher = AntPathMatcher()

    // Map path pattern → Casbin action using AntPathMatcher for path variables
    private val routeMap = listOf(
        Triple("GET",    "/users/list",                    "manage_users"),
        Triple("PUT",    "/users/admin/**",                "manage_users"),
        Triple("DELETE", "/users/admin/**",                "manage_users"),
        Triple("GET",    "/portfolios/user/**",            "view_portfolio"),
        Triple("GET",    "/analytics/**",                  "view_analytics"),
        Triple("GET",    "/strategies/**",                 "create_strategy"),
        Triple("POST",   "/strategies/**",                 "create_strategy"),
        Triple("PUT",    "/strategies/**",                 "create_strategy"),
        Triple("DELETE", "/strategies/**",                 "create_strategy"),
        Triple("POST",   "/portfolios/cash/deposit/**",    "deposit_funds"),
        Triple("GET",    "/roles/requests",                "manage_users"),
        Triple("POST",   "/roles/approve",                 "manage_users"),
        Triple("POST",   "/roles/reject",                  "manage_users"),
        Triple("GET",    "/audit-logs/**",                 "manage_users"),
    )

    private val publicPaths = setOf(
        "/users/signin",
        "/users/login/oauth2/google",
        "/users/generate-token",
        "/users/authenticate-token",
        "/roles/request",
    )

    override fun doFilterInternal(
        req: HttpServletRequest,
        res: HttpServletResponse,
        chain: FilterChain,
    ) {
        val uri    = req.requestURI
        val method = req.method

        // Skip OPTIONS (CORS preflight) and public paths
        if (method == "OPTIONS" || publicPaths.any { pathMatcher.match(it, uri) }) {
            chain.doFilter(req, res); return
        }

        // Find matching action — default allow if no route matches (fail-open for unprotected routes)
        val action = routeMap
            .firstOrNull { (m, pattern, _) ->
                m == method && pathMatcher.match(pattern, uri)
            }?.third

        if (action == null) {
            chain.doFilter(req, res); return
        }

        // Get uid from token stored in localStorage by frontend
        val uid = req.getHeader("X-User-UID")
        if (uid == null) {
            deny(res, 401, "Unauthenticated", "AUTH_MISSING"); return
        }

        // Resolve user from MongoDB
        val user = try {
            userRepository.findOneByUid(uid)
        } catch (e: Exception) {
            log.warn("[CasbinFilter] User not found or DB error for uid=$uid: ${e.message}")
            deny(res, 401, "User not found", "USER_NOT_FOUND"); return
        }

        if (user == null) {
            deny(res, 401, "User not found", "USER_NOT_FOUND"); return
        }

        // Resolve role — isAdmin flag takes precedence
        val role = when {
            user.isAdmin -> "Admin"
            else         -> user.role
        }

        // Enforce permission
        val allowed = enforcer.enforce(role, action, "allow")

        // Audit log every decision
        try {
            auditLogRepository.save(AuditLog(
                event       = if (allowed) "PERMISSION_GRANTED" else "PERMISSION_DENIED",
                userId      = uid,
                role        = role,
                action      = action,
            ))
        } catch (e: Exception) {
            log.warn("[CasbinFilter] Failed to write audit log: ${e.message}")
        }

        if (!allowed) {
            val upgradeAvailable = role == "Viewer" &&
                action in setOf("create_strategy", "execute_strategy", "deposit_funds")
            deny(res, 403,
                "You do not have permission to: $action",
                "PERMISSION_DENIED",
                mapOf("role" to role, "action" to action,
                      "upgradeAvailable" to upgradeAvailable)
            )
            return
        }

        req.setAttribute("userRole", role)
        chain.doFilter(req, res)
    }

    private fun deny(
        res: HttpServletResponse,
        status: Int,
        error: String,
        code: String,
        extra: Map<String, Any> = emptyMap(),
    ) {
        res.status = status
        res.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = mutableMapOf<String, Any>("error" to error, "code" to code)
        body.putAll(extra)
        res.writer.write(mapper.writeValueAsString(body))
    }
}
