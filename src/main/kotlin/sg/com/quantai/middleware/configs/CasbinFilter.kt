package sg.com.quantai.middleware.configs

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.casbin.jcasbin.main.Enforcer
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import sg.com.quantai.middleware.repositories.mongo.UserRepository

@Component
class CasbinFilter(
    private val enforcer: Enforcer,
    private val userRepository: UserRepository,
    private val mapper: ObjectMapper,
) : OncePerRequestFilter() {

    // Map "METHOD:/path" → Casbin action code
    // Add a new line here whenever you add a new protected endpoint
    private val routeMap = mapOf(
        "GET:/users/list"              to "manage_users",
        "PUT:/users/admin"             to "manage_users",
        "DELETE:/users/admin"          to "manage_users",
        "GET:/portfolio"               to "view_portfolio",
        "GET:/analytics"               to "view_analytics",
        "POST:/strategy"               to "create_strategy",
        "POST:/strategy/execute"       to "execute_strategy",
        "POST:/funds/deposit"          to "deposit_funds",
        "GET:/roles/requests"          to "manage_users",   // Admin only
        "POST:/roles/approve"          to "manage_users",   // Admin only
        "POST:/roles/reject"           to "manage_users",   // Admin only
        "GET:/audit-logs"              to "manage_users",
    )

    // These paths skip Casbin entirely — no uid needed
    private val publicPaths = setOf(
        "/users/signin",
        "/users/login/oauth2/google",
        "/users/generate-token",
        "/users/authenticate-token",
        "/actuator/health",
        "/roles/request",
    )

    override fun doFilterInternal(
        req: HttpServletRequest,
        res: HttpServletResponse,
        chain: FilterChain,
    ) {
        // 1. Skip public routes
        if (req.requestURI in publicPaths || req.method == "OPTIONS") {
            chain.doFilter(req, res); return
        }

        // 2. Check if this route needs a permission
        val routeKey = "${req.method}:${req.requestURI}"
        val action = routeMap[routeKey]
        if (action == null) {
            // Route not in map — let it through (not protected)
            chain.doFilter(req, res); return
        }

        // 3. Get uid from request header — frontend sends this after login
        val uid = req.getHeader("X-User-UID")
        if (uid == null) {
            deny(res, 401, "Missing X-User-UID header", "AUTH_MISSING")
            return
        }

        // 4. Look up user in MongoDB to get their role
        val user = try {
            userRepository.findOneByUid(uid)
        } catch (e: Exception) {
            deny(res, 401, "User not found", "USER_NOT_FOUND")
            return
        }

        // 5. Determine role
        // isAdmin=true → Admin, else use the role field (Viewer or Trader)
        val role = when {
            user.isAdmin -> "Admin"
            else        -> user.role  // "Viewer" or "Trader"
        }

        // 6. enforce(role, action, "allow")
        val allowed = enforcer.enforce(role, action, "allow")

        if (!allowed) {
            val upgradeAvailable = role == "Viewer" &&
                action in setOf("create_strategy", "execute_strategy", "deposit_funds")
            deny(res, 403,
                "You do not have permission to: $action",
                "PERMISSION_DENIED",
                mapOf(
                    "role"             to role,
                    "action"           to action,
                    "upgradeAvailable" to upgradeAvailable
                )
            )
            return
        }

        // 7. Attach role for controllers to use if needed
        req.setAttribute("userRole", role)
        chain.doFilter(req, res)
    }

    private fun deny(
        res: HttpServletResponse,
        status: Int,
        error: String,
        code: String,
        extra: Map<String, Any> = emptyMap()
    ) {
        res.status = status
        res.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = mutableMapOf<String, Any>("error" to error, "code" to code)
        body.putAll(extra)
        res.writer.write(mapper.writeValueAsString(body))
    }
}