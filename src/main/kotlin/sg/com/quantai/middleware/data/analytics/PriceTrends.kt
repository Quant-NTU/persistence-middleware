package sg.com.quantai.middleware.data.analytics

import java.math.BigDecimal
import java.time.LocalDateTime

data class PriceTrends(
    val symbol: String,
    val assetType: String,
    val timePeriod: String, // "1h", "1d", "7d", "30d", "90d"
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val openPrice: BigDecimal,
    val closePrice: BigDecimal,
    val highPrice: BigDecimal,
    val lowPrice: BigDecimal,
    val avgPrice: BigDecimal,
    val priceChange: BigDecimal, // Absolute change
    val priceChangePercent: BigDecimal, // % change
    val volatility: BigDecimal? // Standard deviation of prices
)

