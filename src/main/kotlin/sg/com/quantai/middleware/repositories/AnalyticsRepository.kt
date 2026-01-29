package sg.com.quantai.middleware.repositories

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import sg.com.quantai.middleware.data.analytics.*
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDateTime

@Repository
class AnalyticsRepository(private val jdbcTemplate: JdbcTemplate) {

    /**
     * Get volume statistics for a specific time period
     */
    fun getVolumeStats(
        period: String,
        symbols: List<String>?,
        assetType: String?,
        limit: Int,
        offset: Int
    ): List<VolumeStats> {
        val aggregateTable = when (period.lowercase()) {
            "hourly", "1h" -> "ohlc_1hour_aggregate"
            "daily", "1d" -> "ohlc_1day_aggregate"
            "weekly", "7d" -> "ohlc_7day_aggregate"
            else -> "ohlc_1day_aggregate"
        }
        
        val intervalUnit = when (period.lowercase()) {
            "hourly", "1h" -> "hour"
            "daily", "1d" -> "day"
            "weekly", "7d" -> "week"
            else -> "day"
        }

        val whereClauses = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (!symbols.isNullOrEmpty()) {
            val placeholders = symbols.joinToString(",") { "?" }
            whereClauses.add("ds.symbol_code IN ($placeholders)")
            params.addAll(symbols)
        }

        if (!assetType.isNullOrEmpty()) {
            whereClauses.add("dat.asset_type_code = ?")
            params.add(assetType)
        }

        val whereClause = if (whereClauses.isNotEmpty()) {
            "WHERE ${whereClauses.joinToString(" AND ")}"
        } else ""

        val sql = """
            SELECT 
                ds.symbol_code as symbol,
                dat.asset_type_code as asset_type,
                '$period' as time_period,
                agg.bucket as start_time,
                agg.bucket + INTERVAL '1 $intervalUnit' as end_time,
                agg.total_volume,
                agg.avg_volume,
                agg.max_volume,
                agg.min_volume,
                agg.record_count
            FROM $aggregateTable agg
            JOIN dim_symbol ds ON agg.symbol_id = ds.symbol_id
            JOIN dim_asset_type dat ON ds.asset_type_id = dat.asset_type_id
            $whereClause
            ORDER BY agg.bucket DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        params.add(limit)
        params.add(offset)

        return jdbcTemplate.query(sql, params.toTypedArray()) { rs, _ ->
            mapToVolumeStats(rs, period)
        }
    }

    /**
     * Get price trends for specified symbols and date range
     */
    fun getPriceTrends(
        symbols: List<String>,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        period: String,
        limit: Int,
        offset: Int
    ): List<PriceTrends> {
        val aggregateTable = when (period.lowercase()) {
            "hourly", "1h" -> "ohlc_1hour_aggregate"
            "daily", "1d" -> "ohlc_1day_aggregate"
            "weekly", "7d" -> "ohlc_7day_aggregate"
            else -> "ohlc_1day_aggregate"
        }
        
        val intervalUnit = when (period.lowercase()) {
            "hourly", "1h" -> "hour"
            "daily", "1d" -> "day"
            "weekly", "7d" -> "week"
            else -> "day"
        }

        val placeholders = symbols.joinToString(",") { "?" }
        val params = mutableListOf<Any>()
        params.addAll(symbols)
        params.add(startDate)
        params.add(endDate)
        params.add(limit)
        params.add(offset)

        val sql = """
            SELECT 
                ds.symbol_code as symbol,
                dat.asset_type_code as asset_type,
                '$period' as time_period,
                agg.bucket as start_time,
                agg.bucket + INTERVAL '1 $intervalUnit' as end_time,
                agg.first_open as open_price,
                agg.last_close as close_price,
                agg.max_high as high_price,
                agg.min_low as low_price,
                agg.avg_close as avg_price,
                (agg.last_close - agg.first_open) as price_change,
                CASE 
                    WHEN agg.first_open > 0 
                    THEN ((agg.last_close - agg.first_open) / agg.first_open * 100)
                    ELSE 0 
                END as price_change_percent,
                agg.volatility
            FROM $aggregateTable agg
            JOIN dim_symbol ds ON agg.symbol_id = ds.symbol_id
            JOIN dim_asset_type dat ON ds.asset_type_id = dat.asset_type_id
            WHERE ds.symbol_code IN ($placeholders)
            AND agg.bucket BETWEEN ? AND ?
            ORDER BY agg.bucket DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        return jdbcTemplate.query(sql, params.toTypedArray()) { rs, _ ->
            mapToPriceTrends(rs)
        }
    }

    /**
     * Get volatility metrics by asset type
     */
    fun getVolatilityMetrics(
        assetType: String?,
        period: String,
        limit: Int,
        offset: Int
    ): List<VolatilityMetrics> {
        val aggregateTable = when (period.lowercase()) {
            "hourly", "1h" -> "ohlc_1hour_aggregate"
            "daily", "1d" -> "ohlc_1day_aggregate"
            "weekly", "7d" -> "ohlc_7day_aggregate"
            else -> "ohlc_1day_aggregate"
        }
        
        val intervalUnit = when (period.lowercase()) {
            "hourly", "1h" -> "hour"
            "daily", "1d" -> "day"
            "weekly", "7d" -> "week"
            else -> "day"
        }

        val whereClause = if (!assetType.isNullOrEmpty()) {
            "WHERE dat.asset_type_code = ?"
        } else ""

        val params = mutableListOf<Any>()
        if (!assetType.isNullOrEmpty()) {
            params.add(assetType)
        }
        params.add(limit)
        params.add(offset)

        val sql = """
            SELECT 
                ds.symbol_code as symbol,
                dat.asset_type_code as asset_type,
                '$period' as time_period,
                agg.bucket as start_time,
                agg.bucket + INTERVAL '1 $intervalUnit' as end_time,
                agg.volatility,
                AVG(agg.volatility) OVER (PARTITION BY ds.symbol_code ORDER BY agg.bucket 
                    ROWS BETWEEN 5 PRECEDING AND CURRENT ROW) as avg_volatility,
                (agg.max_high - agg.min_low) as max_price_swing,
                AVG(agg.max_high - agg.min_low) OVER (PARTITION BY ds.symbol_code ORDER BY agg.bucket 
                    ROWS BETWEEN 5 PRECEDING AND CURRENT ROW) as avg_price_swing,
                (agg.max_high - agg.min_low) as price_range
            FROM $aggregateTable agg
            JOIN dim_symbol ds ON agg.symbol_id = ds.symbol_id
            JOIN dim_asset_type dat ON ds.asset_type_id = dat.asset_type_id
            $whereClause
            ORDER BY agg.volatility DESC, agg.bucket DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        return jdbcTemplate.query(sql, params.toTypedArray()) { rs, _ ->
            mapToVolatilityMetrics(rs, period)
        }
    }

    /**
     * Compare multiple symbols
     */
    fun compareSymbols(
        symbols: List<String>,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        period: String
    ): ComparisonResult {
        val aggregateTable = when (period.lowercase()) {
            "hourly", "1h" -> "ohlc_1hour_aggregate"
            "daily", "1d" -> "ohlc_1day_aggregate"
            "weekly", "7d" -> "ohlc_7day_aggregate"
            else -> "ohlc_1day_aggregate"
        }

        val placeholders = symbols.joinToString(",") { "?" }
        val params = mutableListOf<Any>()
        params.addAll(symbols)
        params.add(startDate)
        params.add(endDate)

        val sql = """
            WITH symbol_stats AS (
                SELECT 
                    ds.symbol_code as symbol,
                    dat.asset_type_code as asset_type,
                    MIN(agg.bucket) as start_time,
                    MAX(agg.bucket) as end_time,
                    FIRST_VALUE(agg.first_open) OVER (PARTITION BY ds.symbol_code ORDER BY agg.bucket) as initial_price,
                    LAST_VALUE(agg.last_close) OVER (PARTITION BY ds.symbol_code ORDER BY agg.bucket 
                        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) as final_price,
                    SUM(agg.total_volume) as volume_total,
                    AVG(agg.volatility) as avg_volatility,
                    AVG(agg.avg_close) as avg_price
                FROM $aggregateTable agg
                JOIN dim_symbol ds ON agg.symbol_id = ds.symbol_id
                JOIN dim_asset_type dat ON ds.asset_type_id = dat.asset_type_id
                WHERE ds.symbol_code IN ($placeholders)
                AND agg.bucket BETWEEN ? AND ?
                GROUP BY ds.symbol_code, dat.asset_type_code, agg.symbol_id
            )
            SELECT 
                symbol,
                asset_type,
                start_time,
                end_time,
                CASE 
                    WHEN initial_price > 0 
                    THEN ((final_price - initial_price) / initial_price * 100)
                    ELSE 0 
                END as price_change_percent,
                volume_total,
                avg_volatility as volatility,
                avg_price
            FROM symbol_stats
            ORDER BY price_change_percent DESC
        """.trimIndent()

        val comparisons = jdbcTemplate.query(sql, params.toTypedArray()) { rs, _ ->
            mapToSymbolComparison(rs)
        }

        // Determine performance relative to average
        val avgPerformance = if (comparisons.isNotEmpty()) {
            comparisons.map { it.priceChangePercent }.reduce { acc, value -> acc.add(value) }
                .divide(BigDecimal(comparisons.size), 2, BigDecimal.ROUND_HALF_UP)
        } else BigDecimal.ZERO

        val enhancedComparisons = comparisons.map { comparison ->
            val performance = when {
                comparison.priceChangePercent > avgPerformance.multiply(BigDecimal("1.1")) -> "outperforming"
                comparison.priceChangePercent < avgPerformance.multiply(BigDecimal("0.9")) -> "underperforming"
                else -> "neutral"
            }
            comparison.copy(performance = performance)
        }

        return ComparisonResult(
            symbols = symbols,
            timePeriod = period,
            startTime = startDate,
            endTime = endDate,
            comparisons = enhancedComparisons
        )
    }

    // Mapper functions
    private fun mapToVolumeStats(rs: ResultSet, period: String): VolumeStats {
        return VolumeStats(
            symbol = rs.getString("symbol"),
            assetType = rs.getString("asset_type"),
            timePeriod = period,
            startTime = rs.getTimestamp("start_time").toLocalDateTime(),
            endTime = rs.getTimestamp("end_time").toLocalDateTime(),
            totalVolume = rs.getBigDecimal("total_volume"),
            avgVolume = rs.getBigDecimal("avg_volume"),
            maxVolume = rs.getBigDecimal("max_volume"),
            minVolume = rs.getBigDecimal("min_volume"),
            volumeChange = null, // Calculated in service layer with previous period
            recordCount = rs.getLong("record_count")
        )
    }

    private fun mapToPriceTrends(rs: ResultSet): PriceTrends {
        return PriceTrends(
            symbol = rs.getString("symbol"),
            assetType = rs.getString("asset_type"),
            timePeriod = rs.getString("time_period"),
            startTime = rs.getTimestamp("start_time").toLocalDateTime(),
            endTime = rs.getTimestamp("end_time").toLocalDateTime(),
            openPrice = rs.getBigDecimal("open_price"),
            closePrice = rs.getBigDecimal("close_price"),
            highPrice = rs.getBigDecimal("high_price"),
            lowPrice = rs.getBigDecimal("low_price"),
            avgPrice = rs.getBigDecimal("avg_price"),
            priceChange = rs.getBigDecimal("price_change"),
            priceChangePercent = rs.getBigDecimal("price_change_percent"),
            volatility = rs.getBigDecimal("volatility")
        )
    }

    private fun mapToVolatilityMetrics(rs: ResultSet, period: String): VolatilityMetrics {
        return VolatilityMetrics(
            symbol = rs.getString("symbol"),
            assetType = rs.getString("asset_type"),
            timePeriod = period,
            startTime = rs.getTimestamp("start_time").toLocalDateTime(),
            endTime = rs.getTimestamp("end_time").toLocalDateTime(),
            volatility = rs.getBigDecimal("volatility"),
            avgVolatility = rs.getBigDecimal("avg_volatility"),
            maxPriceSwing = rs.getBigDecimal("max_price_swing"),
            avgPriceSwing = rs.getBigDecimal("avg_price_swing"),
            priceRange = rs.getBigDecimal("price_range"),
            volatilityChange = null // Calculated in service layer
        )
    }

    private fun mapToSymbolComparison(rs: ResultSet): SymbolComparison {
        return SymbolComparison(
            symbol = rs.getString("symbol"),
            assetType = rs.getString("asset_type"),
            priceChangePercent = rs.getBigDecimal("price_change_percent"),
            volumeTotal = rs.getBigDecimal("volume_total"),
            volatility = rs.getBigDecimal("volatility"),
            avgPrice = rs.getBigDecimal("avg_price"),
            performance = "" // Set in compareSymbols method
        )
    }
}
