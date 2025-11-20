package sg.com.quantai.middleware.controllers

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import sg.com.quantai.middleware.data.analytics.*
import sg.com.quantai.middleware.services.AnalyticsService
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AnalyticsControllerTest {

    private lateinit var analyticsService: AnalyticsService
    private lateinit var analyticsController: AnalyticsController

    @BeforeEach
    fun setup() {
        analyticsService = mock()
        analyticsController = AnalyticsController(analyticsService)
    }

    @Test
    fun `getVolumeAnalytics should return volume stats with metadata`() {
        // Given
        val volumeStats = listOf(
            VolumeStats(
                symbol = "AAPL",
                assetType = "stock",
                timePeriod = "daily",
                startTime = LocalDateTime.now().minusDays(1),
                endTime = LocalDateTime.now(),
                totalVolume = BigDecimal("1000000"),
                avgVolume = BigDecimal("500000"),
                maxVolume = BigDecimal("750000"),
                minVolume = BigDecimal("250000"),
                volumeChange = BigDecimal("5.5"),
                recordCount = 10
            )
        )

        whenever(analyticsService.getVolumeStats(
            period = "daily",
            symbols = listOf("AAPL"),
            assetType = "stock",
            limit = 100,
            offset = 0
        )).thenReturn(volumeStats)

        // When
        val response = analyticsController.getVolumeAnalytics(
            period = "daily",
            symbols = "AAPL",
            assetType = "stock",
            limit = 100,
            offset = 0
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val data = response.body!!["data"] as List<*>
        assertEquals(1, data.size)
        
        verify(analyticsService).getVolumeStats(
            period = "daily",
            symbols = listOf("AAPL"),
            assetType = "stock",
            limit = 100,
            offset = 0
        )
    }

    @Test
    fun `getVolumeAnalytics should return no content when no stats found`() {
        // Given
        whenever(analyticsService.getVolumeStats(
            period = "daily",
            symbols = null,
            assetType = null,
            limit = 100,
            offset = 0
        )).thenReturn(emptyList())

        // When
        val response = analyticsController.getVolumeAnalytics(
            period = "daily",
            symbols = null,
            assetType = null,
            limit = 100,
            offset = 0
        )

        // Then
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `getVolumeByTimeWindow should return stats for predefined window`() {
        // Given
        val volumeStats = listOf(
            VolumeStats(
                symbol = "MSFT",
                assetType = "stock",
                timePeriod = "daily",
                startTime = LocalDateTime.now().minusDays(7),
                endTime = LocalDateTime.now(),
                totalVolume = BigDecimal("2000000"),
                avgVolume = BigDecimal("285714"),
                maxVolume = BigDecimal("500000"),
                minVolume = BigDecimal("100000"),
                volumeChange = null,
                recordCount = 7
            )
        )

        whenever(analyticsService.getVolumeStatsForTimeWindow(
            window = "week",
            symbols = null,
            assetType = null
        )).thenReturn(volumeStats)

        // When
        val response = analyticsController.getVolumeByTimeWindow(
            window = "week",
            symbols = null,
            assetType = null
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val data = response.body!!["data"] as List<*>
        assertEquals(1, data.size)
    }

    @Test
    fun `getPriceTrends should return trends for symbols in date range`() {
        // Given
        val startDate = LocalDateTime.now().minusDays(30)
        val endDate = LocalDateTime.now()
        val priceTrends = listOf(
            PriceTrends(
                symbol = "GOOGL",
                assetType = "stock",
                timePeriod = "daily",
                startTime = startDate,
                endTime = endDate,
                openPrice = BigDecimal("2800.00"),
                closePrice = BigDecimal("2850.00"),
                highPrice = BigDecimal("2900.00"),
                lowPrice = BigDecimal("2750.00"),
                avgPrice = BigDecimal("2825.00"),
                priceChange = BigDecimal("50.00"),
                priceChangePercent = BigDecimal("1.79"),
                volatility = BigDecimal("45.50")
            )
        )

        whenever(analyticsService.getPriceTrends(
            symbols = listOf("GOOGL"),
            startDate = startDate,
            endDate = endDate,
            period = "daily",
            limit = 100,
            offset = 0
        )).thenReturn(priceTrends)

        // When
        val response = analyticsController.getPriceTrends(
            symbols = "GOOGL",
            startDate = startDate,
            endDate = endDate,
            period = "daily",
            limit = 100,
            offset = 0
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val data = response.body!!["data"] as List<*>
        assertEquals(1, data.size)
    }

    @Test
    fun `getPriceTrends should return bad request when no symbols provided`() {
        // When
        val response = analyticsController.getPriceTrends(
            symbols = "",
            startDate = LocalDateTime.now().minusDays(7),
            endDate = LocalDateTime.now(),
            period = "daily",
            limit = 100,
            offset = 0
        )

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `getVolatilityMetrics should return volatility data`() {
        // Given
        val volatilityMetrics = listOf(
            VolatilityMetrics(
                symbol = "TSLA",
                assetType = "stock",
                timePeriod = "daily",
                startTime = LocalDateTime.now().minusDays(1),
                endTime = LocalDateTime.now(),
                volatility = BigDecimal("85.50"),
                avgVolatility = BigDecimal("80.25"),
                maxPriceSwing = BigDecimal("120.00"),
                avgPriceSwing = BigDecimal("95.50"),
                priceRange = BigDecimal("150.00"),
                volatilityChange = BigDecimal("6.54")
            )
        )

        whenever(analyticsService.getVolatilityMetrics(
            assetType = "stock",
            period = "daily",
            limit = 100,
            offset = 0
        )).thenReturn(volatilityMetrics)

        // When
        val response = analyticsController.getVolatilityMetrics(
            assetType = "stock",
            period = "daily",
            limit = 100,
            offset = 0
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        val data = response.body!!["data"] as List<*>
        assertEquals(1, data.size)
    }

    @Test
    fun `compareSymbols should return comparison results`() {
        // Given
        val startDate = LocalDateTime.now().minusDays(30)
        val endDate = LocalDateTime.now()
        val comparisonResult = ComparisonResult(
            symbols = listOf("AAPL", "MSFT", "GOOGL"),
            timePeriod = "daily",
            startTime = startDate,
            endTime = endDate,
            comparisons = listOf(
                SymbolComparison(
                    symbol = "AAPL",
                    assetType = "stock",
                    priceChangePercent = BigDecimal("15.5"),
                    volumeTotal = BigDecimal("50000000"),
                    volatility = BigDecimal("35.2"),
                    avgPrice = BigDecimal("175.50"),
                    performance = "outperforming"
                ),
                SymbolComparison(
                    symbol = "MSFT",
                    assetType = "stock",
                    priceChangePercent = BigDecimal("12.3"),
                    volumeTotal = BigDecimal("40000000"),
                    volatility = BigDecimal("28.5"),
                    avgPrice = BigDecimal("380.25"),
                    performance = "neutral"
                ),
                SymbolComparison(
                    symbol = "GOOGL",
                    assetType = "stock",
                    priceChangePercent = BigDecimal("8.7"),
                    volumeTotal = BigDecimal("30000000"),
                    volatility = BigDecimal("42.1"),
                    avgPrice = BigDecimal("2825.00"),
                    performance = "underperforming"
                )
            )
        )

        val request = ComparisonRequest(
            symbols = listOf("AAPL", "MSFT", "GOOGL"),
            startDate = startDate,
            endDate = endDate,
            period = "daily"
        )

        whenever(analyticsService.compareSymbols(
            symbols = listOf("AAPL", "MSFT", "GOOGL"),
            startDate = startDate,
            endDate = endDate,
            period = "daily"
        )).thenReturn(comparisonResult)

        // When
        val response = analyticsController.compareSymbols(request)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
    }

    @Test
    fun `compareSymbols should return bad request when less than 2 symbols provided`() {
        // Given
        val request = ComparisonRequest(
            symbols = listOf("AAPL"),
            startDate = LocalDateTime.now().minusDays(30),
            endDate = LocalDateTime.now(),
            period = "daily"
        )

        // When
        val response = analyticsController.compareSymbols(request)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `compareSymbols should return no content when comparison fails`() {
        // Given
        val startDate = LocalDateTime.now().minusDays(30)
        val endDate = LocalDateTime.now()
        val request = ComparisonRequest(
            symbols = listOf("AAPL", "MSFT"),
            startDate = startDate,
            endDate = endDate,
            period = "daily"
        )

        whenever(analyticsService.compareSymbols(
            symbols = listOf("AAPL", "MSFT"),
            startDate = startDate,
            endDate = endDate,
            period = "daily"
        )).thenReturn(null)

        // When
        val response = analyticsController.compareSymbols(request)

        // Then
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }
}
