package sg.com.quantai.middleware.data.analytics

import java.math.BigDecimal
import java.time.LocalDateTime

data class VolumeStats(
    val symbol: String,
    val assetType: String,
    val timePeriod: String, // "daily", "weekly", "monthly", "quarterly"
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val totalVolume: BigDecimal,
    val avgVolume: BigDecimal,
    val maxVolume: BigDecimal,
    val minVolume: BigDecimal,
    val volumeChange: BigDecimal?, // % change from previous period
    val recordCount: Long
)

