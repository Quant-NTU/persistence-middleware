package sg.com.quantai.middleware.requests.assets

import java.math.BigDecimal

class ForexRequest (
    val portfolio_uid: String,
    val name: String,
    val quantity: BigDecimal,
    val purchasePrice: BigDecimal,
    val currencyPair: String
)