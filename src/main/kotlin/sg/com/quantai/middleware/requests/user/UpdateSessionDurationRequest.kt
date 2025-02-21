package sg.com.quantai.middleware.requests.user

import java.math.BigDecimal

class UpdateSessionDurationRequest(
    val email: String,
    val sessionDuration: BigDecimal
)