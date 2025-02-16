package sg.com.quantai.middleware.requests
import java.math.BigDecimal


class NewPortfolioRequest(
        val isMain: String,
        val description: String,
        val name: String,
)