package sg.com.quantai.middleware.requests
import java.math.BigDecimal


class NewPortfolioRequest(
        val isMain: Boolean,
        val assets: String,
        val history: String,
)