package sg.com.quantai.middleware.requests.assets

import java.math.BigDecimal

class StockRequest (
    val portfolio_uid: String,
    val name: String,
    val quantity: BigDecimal,
    val purchasePrice: BigDecimal,
    val ticker: String
)

class DeleteStockRequest(
    val portfolio_uid: String,
    val name: String,
    val quantity: BigDecimal,
    val deleteAll: Boolean
)