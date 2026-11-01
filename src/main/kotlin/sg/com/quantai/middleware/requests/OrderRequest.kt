package sg.com.quantai.middleware.requests

import java.math.BigDecimal
import sg.com.quantai.middleware.data.mongo.enums.AssetType
import sg.com.quantai.middleware.data.mongo.enums.OrderSide
import sg.com.quantai.middleware.data.mongo.enums.OrderType

data class CreateOrderRequest(
    val portfolioId: String,
    val symbol: String,
    val side: OrderSide,
    val quantity: BigDecimal,
    val orderType: OrderType,
    val limitPrice: BigDecimal? = null,
    val assetType: AssetType? = null,
    val name: String? = null,
)
