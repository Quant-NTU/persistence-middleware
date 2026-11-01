package sg.com.quantai.middleware.data.mongo.enums

import com.fasterxml.jackson.annotation.JsonCreator

enum class OrderSide {
    BUY,
    SELL,
}

enum class OrderType {
    MARKET,
    LIMIT,
}

enum class OrderStatus {
    PENDING,
    FILLED,
    PARTIALLY_FILLED,
    REJECTED,
    CANCELLED,
}

enum class AssetType {
    STOCK,
    CRYPTO,
    FOREX,
    ;

    companion object {
        @JvmStatic
        @JsonCreator
        fun from(value: String?): AssetType {
            return when (value?.trim()?.uppercase()) {
                "STOCK" -> STOCK
                "CRYPTO" -> CRYPTO
                "FOREX" -> FOREX
                else -> throw IllegalArgumentException("Unknown asset type: $value")
            }
        }
    }
}
