package sg.com.quantai.middleware.services

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import sg.com.quantai.middleware.data.analytics.*
import sg.com.quantai.middleware.repositories.AnalyticsRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsServiceTest {

    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var analyticsService: AnalyticsService

    @BeforeEach
    fun setup() {
        analyticsRepository = mock()
        analyticsService = AnalyticsService(analyticsRepository)
    }

    @Test
    fun `getVolumeStats should return volume statistics`() {
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
                volumeChange = null,
                recordCount = 10
            )
        )

        whenever(analyticsRepository.getVolumeStats(
            period = "daily",
            symbols = listOf("AAPL"),
            assetType = "stock",
            limit = 100,
            offset = 0
        )).thenReturn(volumeStats)

        // When
        val result = analyticsService.getVolumeStats(
            period = "daily",
            symbols = listOf("AAPL"),
            assetType = "stock"
        )

        // Then
        assertEquals(1, result.size)
        assertEquals("AAPL", result[0].symbol)
        verify(analyticsRepository).getVolumeStats(
            period = "daily",
            symbols = listOf("AAPL"),
            assetType = "stock",
            limit = 100,
            offset = 0
        )
    }

    @Test
    fun `getVolumeStats should validate and apply default limit`() {
        // Given
        whenever(analyticsRepository.getVolumeStats(
            period = "daily",
            symbols = null,
            assetType = null,
            limit = 100,
            offset = 0
        )).thenReturn(emptyList())

        // When
        analyticsService.getVolumeStats(period = "daily", limit = -1)

        // Then
        verify(analyticsRepository).getVolumeStats(
            period = "daily",
            symbols = null,
            assetType = null,
            limit = 100, // Should use default
            offset = 0
        )
    }

    @Test
    fun `getVolumeStats should cap limit at MAX_LIMIT`() {
        // Given
        whenever(analyticsRepository.getVolumeStats(
            period = "daily",
            symbols = null,
            assetType = null,
            limit = 1000,
            offset = 0
        )).thenReturn(emptyList())

        // When
        analyticsService.getVolumeStats(period = "daily", limit = 9999)

        // Then
        verify(analyticsRepository).getVolumeStats(
            period = "daily",
            symbols = null,
            assetType = null,
            limit = 1000, // Should cap at MAX_LIMIT
            offset = 0
        )
    }

    @Test
    fun `getVolumeStats should return empty list on error`() {
        // Given
        whenever(analyticsRepository.getVolumeStats(
            any(), any(), any(), any(), any()
        )).thenThrow(RuntimeException("Database error"))

        // When
        val result = analyticsService.getVolumeStats(period = "daily")

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getPriceTrends should return price trends`() {
        // Given
        val startDate = LocalDateTime.now().minusDays(7)
        val endDate = LocalDateTime.now()
        val priceTrends = listOf(
            PriceTrends(
                symbol = "MSFT",
                assetType = "stock",
                timePeriod = "daily",
                startTime = startDate,
                endTime = endDate,
                openPrice = BigDecimal("380.00"),
                closePrice = BigDecimal("395.00"),
                highPrice = BigDecimal("400.00"),
                lowPrice = BigDecimal("375.00"),
                avgPrice = BigDecimal("387.50"),
                priceChange = BigDecimal("15.00"),
                priceChangePercent = BigDecimal("3.95"),
                volatility = BigDecimal("12.5")
            )
        )

        whenever(analyticsRepository.getPriceTrends(
            symbols = listOf("MSFT"),
            startDate = startDate,
            endDate = endDate,
            period = "daily",
            limit = 100,
            offset = 0
        )).thenReturn(priceTrends)

        // When
        val result = analyticsService.getPriceTrends(
            symbols = listOf("MSFT"),
            startDate = startDate,
            endDate = endDate
        )

        // Then
        assertEquals(1, result.size)
        assertEquals("MSFT", result[0].symbol)
        assertEquals(BigDecimal("3.95"), result[0].priceChangePercent)
    }

    @Test
    fun `getPriceTrends should return empty list when no symbols provided`() {
        // When
        val result = analyticsService.getPriceTrends(
            symbols = emptyList(),
            startDate = LocalDateTime.now().minusDays(7),
            endDate = LocalDateTime.now()
        )

        // Then
        assertTrue(result.isEmpty())
        verify(analyticsRepository, never()).getPriceTrends(any(), any(), any(), any(), any(), any())
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
                volatility = BigDecimal("75.5"),
                avgVolatility = BigDecimal("70.2"),
                maxPriceSwing = BigDecimal("100.0"),
                avgPriceSwing = BigDecimal("85.5"),
                priceRange = BigDecimal("120.0"),
                volatilityChange = BigDecimal("7.55")
            )
        )

        whenever(analyticsRepository.getVolatilityMetrics(
            assetType = "stock",
            period = "daily",
            limit = 100,
            offset = 0
        )).thenReturn(volatilityMetrics)

        // When
        val result = analyticsService.getVolatilityMetrics(assetType = "stock")

        // Then
        assertEquals(1, result.size)
        assertEquals("TSLA", result[0].symbol)
        assertEquals(BigDecimal("75.5"), result[0].volatility)
    }

    @Test
    fun `compareSymbols should return comparison result`() {
        // Given
        val startDate = LocalDateTime.now().minusDays(30)
        val endDate = LocalDateTime.now()
        val comparisonResult = ComparisonResult(
            symbols = listOf("AAPL", "MSFT"),
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
                )
            )
        )

        whenever(analyticsRepository.compareSymbols(
            symbols = listOf("AAPL", "MSFT"),
            startDate = startDate,
            endDate = endDate,
            period = "daily"
        )).thenReturn(comparisonResult)

        // When
        val result = analyticsService.compareSymbols(
            symbols = listOf("AAPL", "MSFT"),
            startDate = startDate,
            endDate = endDate
        )

        // Then
        assertNotNull(result)
        assertEquals(2, result.comparisons.size)
        assertEquals("AAPL", result.comparisons[0].symbol)
    }

    @Test
    fun `compareSymbols should return null when no symbols provided`() {
        // When
        val result = analyticsService.compareSymbols(
            symbols = emptyList(),
            startDate = LocalDateTime.now().minusDays(30),
            endDate = LocalDateTime.now()
        )

        // Then
        assertNull(result)
    }

    @Test
    fun `compareSymbols should return null when only one symbol provided`() {
        // When
        val result = analyticsService.compareSymbols(
            symbols = listOf("AAPL"),
            startDate = LocalDateTime.now().minusDays(30),
            endDate = LocalDateTime.now()
        )

        // Then
        assertNull(result)
    }

    @Test
    fun `getVolumeStatsForTimeWindow should handle today window`() {
        // Given
        whenever(analyticsRepository.getVolumeStats(
            period = any(),
            symbols = any(),
            assetType = any(),
            limit = any(),
            offset = any()
        )).thenReturn(emptyList())

        // When
        analyticsService.getVolumeStatsForTimeWindow("today")

        // Then
        verify(analyticsRepository).getVolumeStats(
            period = eq("hourly"),
            symbols = isNull(),
            assetType = isNull(),
            limit = eq(100),
            offset = eq(0)
        )
    }

    @Test
    fun `getVolumeStatsForTimeWindow should handle week window`() {
        // Given
        whenever(analyticsRepository.getVolumeStats(
            period = any(),
            symbols = any(),
            assetType = any(),
            limit = any(),
            offset = any()
        )).thenReturn(emptyList())

        // When
        analyticsService.getVolumeStatsForTimeWindow("week")

        // Then
        verify(analyticsRepository).getVolumeStats(
            period = eq("daily"),
            symbols = isNull(),
            assetType = isNull(),
            limit = eq(100),
            offset = eq(0)
        )
    }

    @Test
    fun `getVolumeStatsForTimeWindow should handle month window`() {
        // Given
        whenever(analyticsRepository.getVolumeStats(
            period = any(),
            symbols = any(),
            assetType = any(),
            limit = any(),
            offset = any()
        )).thenReturn(emptyList())

        // When
        analyticsService.getVolumeStatsForTimeWindow("month")

        // Then
        verify(analyticsRepository).getVolumeStats(
            period = eq("daily"),
            symbols = isNull(),
            assetType = isNull(),
            limit = eq(100),
            offset = eq(0)
        )
    }

    @Test
    fun `getVolumeStatsForTimeWindow should handle quarter window`() {
        // Given
        whenever(analyticsRepository.getVolumeStats(
            period = any(),
            symbols = any(),
            assetType = any(),
            limit = any(),
            offset = any()
        )).thenReturn(emptyList())

        // When
        analyticsService.getVolumeStatsForTimeWindow("quarter")

        // Then
        verify(analyticsRepository).getVolumeStats(
            period = eq("weekly"),
            symbols = isNull(),
            assetType = isNull(),
            limit = eq(100),
            offset = eq(0)
        )
    }
}
