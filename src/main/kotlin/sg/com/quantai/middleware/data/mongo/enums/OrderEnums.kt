package sg.com.quantai.middleware.data.mongo.enums

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
}
