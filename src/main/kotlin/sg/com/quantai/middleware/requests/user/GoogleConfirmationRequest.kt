package sg.com.quantai.middleware.requests.user

class GoogleConfirmationRequest(
    val oauthEmail: String,
    val registrationEmail: String,
    val name: String,
    val password: String,
    val salt: String
)