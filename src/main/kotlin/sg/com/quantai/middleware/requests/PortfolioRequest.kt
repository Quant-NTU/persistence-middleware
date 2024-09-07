package sg.com.quantai.middleware.requests

import java.math.BigDecimal

class PortfolioRequest(
        val symbol: String,
        val name: String,
        val quantity: BigDecimal,
        val price: BigDecimal,
        val platform: String
)