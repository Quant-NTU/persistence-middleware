package sg.com.quantai.middleware.data.analytics

import java.math.BigDecimal
import java.time.LocalDateTime

data class VolatilityMetrics(
    val symbol: String,
    val assetType: String,
    val timePeriod: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val volatility: BigDecimal, // Standard deviation
    val avgVolatility: BigDecimal,
    val maxPriceSwing: BigDecimal, // Max(high-low)
    val avgPriceSwing: BigDecimal,
    val priceRange: BigDecimal, // Overall high - low
    val volatilityChange: BigDecimal? // % change from previous period
)

