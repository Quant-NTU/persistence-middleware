package sg.com.quantai.middleware.requests.user

data class EmailPasswordResetRequest(
    val email: String,
    val resetPasswordToken: String,
    val newPassword: String
)
