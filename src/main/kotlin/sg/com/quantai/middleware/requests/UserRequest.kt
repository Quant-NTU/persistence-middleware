package sg.com.quantai.middleware.requests

import org.bson.types.ObjectId
import java.math.BigDecimal

class UserRequest(
        val name: String,
        val email: String,
        val password: String,
)

class LoginRequest(
        val email: String,
        val password: String,
)

class NameEmailRequest(
        val name: String,
        val email: String
)

class AuthenticationRequest(
        val email: String,
        val resetPasswordToken: String
)

class PasswordRequest(
        val newPassword: String, 
)

class GoogleConfirmationRequest(
        val oauthEmail: String,
        val registrationEmail: String,
        val name: String,
        val password: String,
        val salt: String
)

class AccountConfirmationRequest(
        val registrationEmail: String,
        val password: String,
        val oauthEmail: String
)

class AdminRequest(
        val requesterUid: String
)

class Enable2FARequest(
        val secret2FA: String
)

class UpdateSessionDurationRequest(
        val email: String,
        val sessionDuration: BigDecimal
)