package sg.com.quantai.middleware.responses.auth

data class AuthUser(
    val uid: String,
    val name: String,
    val email: String,
    val role: String,
    val isAdmin: Boolean,
    val has2fa: Boolean
)

data class LoginSuccessResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 1800,
    val user: AuthUser
)

data class TwoFactorRequiredResponse(
    val requires2FA: Boolean = true,
    val challengeToken: String
)

data class Verify2FARequest(
    val challengeToken: String,
    val code: String
)
