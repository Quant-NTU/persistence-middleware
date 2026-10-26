
package sg.com.quantai.middleware.requests.user

data class Enable2FARequest(
    val secret2FA: String,
    val code: String
)
