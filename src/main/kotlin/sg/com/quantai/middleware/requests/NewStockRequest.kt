package sg.com.quantai.middleware.requests
import java.math.BigDecimal


class NewStockRequest(
        val name: String,
        val symbol: String,
        val quantity: BigDecimal,
        val purchasePrice: BigDecimal,
)