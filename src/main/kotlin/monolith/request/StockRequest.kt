package monolith.request

import java.math.BigDecimal
import java.time.LocalDateTime

class StockRequest (
    val name: String,
    val symbol: String,
    val marketCap: BigDecimal,
    val price: BigDecimal,
    val description: String = "None",
    val change: BigDecimal,
    val volume: String
)