package sg.com.quantai.middleware.requests.user

class AuthenticationRequest(
    val email: String,
    val resetPasswordToken: String
)