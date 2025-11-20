package sg.com.quantai.middleware.requests

import java.math.BigDecimal

class PortfolioRequest(
        val description: String,
        val name: String,
        val cashBalance: BigDecimal = BigDecimal.ZERO
)