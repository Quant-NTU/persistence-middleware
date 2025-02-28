package sg.com.quantai.middleware.requests.assets

import java.math.BigDecimal

class CryptoRequest (
    val portfolio_uid: String,
    val name: String,
    val quantity: BigDecimal,
    val purchasePrice: BigDecimal,
    val symbol: String,
    val action: String? = "Add",
)