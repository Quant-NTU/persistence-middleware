package sg.com.quantai.middleware.services

import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import sg.com.quantai.middleware.data.analytics.*
import sg.com.quantai.middleware.repositories.AnalyticsRepository
import java.time.LocalDateTime

@Service
class AnalyticsService(private val analyticsRepository: AnalyticsRepository) {

    private val logger = LoggerFactory.getLogger(AnalyticsService::class.java)

    companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 1000
    }

    /**
     * Get volume statistics with optional filtering
     */
    @Cacheable(value = ["analytics"], key = "'volume_' + #period + '_' + #symbols + '_' + #assetType + '_' + #limit + '_' + #offset")
    fun getVolumeStats(
        period: String,
        symbols: List<String>? = null,
        assetType: String? = null,
        limit: Int = DEFAULT_LIMIT,
        offset: Int = 0
    ): List<VolumeStats> {
        logger.info("Fetching volume stats for period: $period, symbols: $symbols, assetType: $assetType")
        
        val effectiveLimit = validateLimit(limit)
        val effectiveOffset = if (offset < 0) 0 else offset

        return try {
            analyticsRepository.getVolumeStats(period, symbols, assetType, effectiveLimit, effectiveOffset)
        } catch (e: Exception) {
            logger.error("Error fetching volume stats", e)
            emptyList()
        }
    }

    /**
     * Get price trends for specific symbols and date range
     */
    @Cacheable(value = ["analytics"], key = "'trends_' + #symbols + '_' + #startDate + '_' + #endDate + '_' + #period")
    fun getPriceTrends(
        symbols: List<String>,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        period: String = "daily",
        limit: Int = DEFAULT_LIMIT,
        offset: Int = 0
    ): List<PriceTrends> {
        logger.info("Fetching price trends for symbols: $symbols from $startDate to $endDate")
        
        if (symbols.isEmpty()) {
            logger.warn("No symbols provided for price trends")
            return emptyList()
        }

        val effectiveLimit = validateLimit(limit)
        val effectiveOffset = if (offset < 0) 0 else offset

        return try {
            analyticsRepository.getPriceTrends(symbols, startDate, endDate, period, effectiveLimit, effectiveOffset)
        } catch (e: Exception) {
            logger.error("Error fetching price trends", e)
            emptyList()
        }
    }

    /**
     * Get volatility metrics filtered by asset type
     */
    @Cacheable(value = ["analytics"], key = "'volatility_' + #assetType + '_' + #period + '_' + #limit + '_' + #offset")
    fun getVolatilityMetrics(
        assetType: String? = null,
        period: String = "daily",
        limit: Int = DEFAULT_LIMIT,
        offset: Int = 0
    ): List<VolatilityMetrics> {
        logger.info("Fetching volatility metrics for assetType: $assetType, period: $period")
        
        val effectiveLimit = validateLimit(limit)
        val effectiveOffset = if (offset < 0) 0 else offset

        return try {
            analyticsRepository.getVolatilityMetrics(assetType, period, effectiveLimit, effectiveOffset)
        } catch (e: Exception) {
            logger.error("Error fetching volatility metrics", e)
            emptyList()
        }
    }

    /**
     * Compare multiple symbols over a time period
     */
    @Cacheable(value = ["analytics"], key = "'compare_' + #symbols + '_' + #startDate + '_' + #endDate + '_' + #period")
    fun compareSymbols(
        symbols: List<String>,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        period: String = "daily"
    ): ComparisonResult? {
        logger.info("Comparing symbols: $symbols from $startDate to $endDate")
        
        if (symbols.isEmpty()) {
            logger.warn("No symbols provided for comparison")
            return null
        }

        if (symbols.size < 2) {
            logger.warn("At least 2 symbols required for comparison")
            return null
        }

        return try {
            analyticsRepository.compareSymbols(symbols, startDate, endDate, period)
        } catch (e: Exception) {
            logger.error("Error comparing symbols", e)
            null
        }
    }

    /**
     * Get volume statistics for predefined time windows
     */
    fun getVolumeStatsForTimeWindow(
        window: String, // "today", "week", "month", "quarter"
        symbols: List<String>? = null,
        assetType: String? = null
    ): List<VolumeStats> {
        val (startDate, endDate, period) = when (window.lowercase()) {
            "today" -> Triple(
                LocalDateTime.now().withHour(0).withMinute(0).withSecond(0),
                LocalDateTime.now(),
                "hourly"
            )
            "week" -> Triple(
                LocalDateTime.now().minusDays(7),
                LocalDateTime.now(),
                "daily"
            )
            "month" -> Triple(
                LocalDateTime.now().minusDays(30),
                LocalDateTime.now(),
                "daily"
            )
            "quarter" -> Triple(
                LocalDateTime.now().minusDays(90),
                LocalDateTime.now(),
                "weekly"
            )
            else -> Triple(
                LocalDateTime.now().minusDays(7),
                LocalDateTime.now(),
                "daily"
            )
        }

        logger.info("Fetching volume stats for window: $window ($startDate to $endDate)")
        return getVolumeStats(period, symbols, assetType)
    }

    private fun validateLimit(limit: Int): Int {
        return when {
            limit <= 0 -> DEFAULT_LIMIT
            limit > MAX_LIMIT -> MAX_LIMIT
            else -> limit
        }
    }
}
