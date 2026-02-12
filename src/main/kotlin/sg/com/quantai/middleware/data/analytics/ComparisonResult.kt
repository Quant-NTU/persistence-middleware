package sg.com.quantai.middleware.data.analytics

import java.math.BigDecimal
import java.time.LocalDateTime

data class ComparisonResult(
    val symbols: List<String>,
    val timePeriod: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val comparisons: List<SymbolComparison>
)

data class SymbolComparison(
    val symbol: String,
    val assetType: String,
    val priceChangePercent: BigDecimal,
    val volumeTotal: BigDecimal,
    val volatility: BigDecimal,
    val avgPrice: BigDecimal,
    val performance: String // "outperforming", "underperforming", "neutral"
)

