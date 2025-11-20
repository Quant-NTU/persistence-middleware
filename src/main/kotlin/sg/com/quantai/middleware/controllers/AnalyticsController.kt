package sg.com.quantai.middleware.controllers

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.data.analytics.*
import sg.com.quantai.middleware.services.AnalyticsService
import java.time.LocalDateTime

@RestController
@RequestMapping("/analytics")
class AnalyticsController(private val analyticsService: AnalyticsService) {

    /**
     * GET /analytics/volume
     * Returns volume statistics with time-period parameter
     * 
     * Query params:
     * - period: hourly|daily|weekly (default: daily)
     * - symbols: comma-separated list of symbols (optional)
     * - assetType: stock|forex|crypto (optional)
     * - limit: max results (default: 100)
     * - offset: pagination offset (default: 0)
     */
    @GetMapping("/volume")
    fun getVolumeAnalytics(
        @RequestParam(defaultValue = "daily") period: String,
        @RequestParam(required = false) symbols: String?,
        @RequestParam(required = false) assetType: String?,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<Map<String, Any>> {
        val symbolList = symbols?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        
        val stats = analyticsService.getVolumeStats(
            period = period,
            symbols = symbolList,
            assetType = assetType,
            limit = limit,
            offset = offset
        )

        val response = mapOf(
            "data" to stats,
            "metadata" to mapOf(
                "count" to stats.size,
                "limit" to limit,
                "offset" to offset,
                "period" to period
            )
        )

        return if (stats.isEmpty()) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.ok(response)
        }
    }

    /**
     * GET /analytics/volume/window
     * Get volume statistics for predefined time windows
     * 
     * Query params:
     * - window: today|week|month|quarter (default: week)
     * - symbols: comma-separated list (optional)
     * - assetType: stock|forex|crypto (optional)
     */
    @GetMapping("/volume/window")
    fun getVolumeByTimeWindow(
        @RequestParam(defaultValue = "week") window: String,
        @RequestParam(required = false) symbols: String?,
        @RequestParam(required = false) assetType: String?
    ): ResponseEntity<Map<String, Any>> {
        val symbolList = symbols?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        
        val stats = analyticsService.getVolumeStatsForTimeWindow(
            window = window,
            symbols = symbolList,
            assetType = assetType
        )

        val response = mapOf(
            "data" to stats,
            "metadata" to mapOf(
                "count" to stats.size,
                "window" to window
            )
        )

        return if (stats.isEmpty()) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.ok(response)
        }
    }

    /**
     * GET /analytics/trends
     * Returns price trends for specified symbols and date range
     * 
     * Query params:
     * - symbols: comma-separated list (required)
     * - startDate: ISO datetime (required)
     * - endDate: ISO datetime (required)
     * - period: hourly|daily|weekly (default: daily)
     * - limit: max results (default: 100)
     * - offset: pagination offset (default: 0)
     */
    @GetMapping("/trends")
    fun getPriceTrends(
        @RequestParam symbols: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime,
        @RequestParam(defaultValue = "daily") period: String,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<Map<String, Any>> {
        val symbolList = symbols.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (symbolList.isEmpty()) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to "At least one symbol is required")
            )
        }

        val trends = analyticsService.getPriceTrends(
            symbols = symbolList,
            startDate = startDate,
            endDate = endDate,
            period = period,
            limit = limit,
            offset = offset
        )

        val response = mapOf(
            "data" to trends,
            "metadata" to mapOf(
                "count" to trends.size,
                "symbols" to symbolList,
                "startDate" to startDate,
                "endDate" to endDate,
                "period" to period,
                "limit" to limit,
                "offset" to offset
            )
        )

        return if (trends.isEmpty()) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.ok(response)
        }
    }

    /**
     * GET /analytics/volatility
     * Returns volatility metrics by asset type
     * 
     * Query params:
     * - assetType: stock|forex|crypto (optional)
     * - period: hourly|daily|weekly (default: daily)
     * - limit: max results (default: 100)
     * - offset: pagination offset (default: 0)
     */
    @GetMapping("/volatility")
    fun getVolatilityMetrics(
        @RequestParam(required = false) assetType: String?,
        @RequestParam(defaultValue = "daily") period: String,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<Map<String, Any>> {
        val metrics = analyticsService.getVolatilityMetrics(
            assetType = assetType,
            period = period,
            limit = limit,
            offset = offset
        )

        val response = mapOf(
            "data" to metrics,
            "metadata" to mapOf(
                "count" to metrics.size,
                "assetType" to (assetType ?: "all"),
                "period" to period,
                "limit" to limit,
                "offset" to offset
            )
        )

        return if (metrics.isEmpty()) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.ok(response)
        }
    }

    /**
     * POST /analytics/compare
     * Supports multi-symbol comparison analysis
     * 
     * Request body:
     * {
     *   "symbols": ["AAPL", "MSFT", "GOOGL"],
     *   "startDate": "2024-01-01T00:00:00",
     *   "endDate": "2024-12-31T23:59:59",
     *   "period": "daily"
     * }
     */
    @PostMapping("/compare")
    fun compareSymbols(
        @RequestBody request: ComparisonRequest
    ): ResponseEntity<Any> {
        if (request.symbols.size < 2) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to "At least 2 symbols are required for comparison")
            )
        }

        val result = analyticsService.compareSymbols(
            symbols = request.symbols,
            startDate = request.startDate,
            endDate = request.endDate,
            period = request.period ?: "daily"
        )

        return if (result == null) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.ok(result)
        }
    }
}

/**
 * Request body for POST /analytics/compare
 */
data class ComparisonRequest(
    val symbols: List<String>,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val startDate: LocalDateTime,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val endDate: LocalDateTime,
    val period: String? = "daily"
)
