package sg.com.quantai.middleware.requests.user

data class PasswordRequest(
    val newPassword: String,
    val resetPasswordToken: String
)
