package sg.com.quantai.middleware.requests.user

class AccountConfirmationRequest(
    val registrationEmail: String,
    val password: String,
    val oauthEmail: String
)