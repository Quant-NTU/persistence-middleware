package sg.com.quantai.middleware.repositories

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import sg.com.quantai.middleware.data.analytics.*
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AnalyticsRepositoryTest {

    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var analyticsRepository: AnalyticsRepository

    @BeforeEach
    fun setup() {
        jdbcTemplate = mock()
        analyticsRepository = AnalyticsRepository(jdbcTemplate)
    }

    @Test
    fun `getVolumeStats should execute query with correct parameters for daily period`() {
        // Given
        val symbols = listOf("AAPL", "MSFT")
        val assetType = "stock"
        
        whenever(jdbcTemplate.query(
            any<String>(),
            any<Array<Any>>(),
            any<RowMapper<VolumeStats>>()
        )).thenReturn(emptyList())

        // When
        analyticsRepository.getVolumeStats(
            period = "daily",
            symbols = symbols,
            assetType = assetType,
            limit = 100,
            offset = 0
        )

        // Then
        verify(jdbcTemplate).query(
            argThat<String> { this.contains("ohlc_1day_aggregate") },
            argThat<Array<Any>> { 
                this.size == 5 && 
                this[0] == "AAPL" && 
                this[1] == "MSFT" && 
                this[2] == "stock" &&
                this[3] == 100 &&
                this[4] == 0
            },
            any<RowMapper<VolumeStats>>()
        )
    }

    @Test
    fun `getVolumeStats should use hourly aggregate for hourly period`() {
        // Given
        whenever(jdbcTemplate.query(
            any<String>(),
            any<Array<Any>>(),
            any<RowMapper<VolumeStats>>()
        )).thenReturn(emptyList())

        // When
        analyticsRepository.getVolumeStats(
            period = "hourly",
            symbols = null,
            assetType = null,
            limit = 50,
            offset = 10
        )

        // Then
        verify(jdbcTemplate).query(
            argThat<String> { this.contains("ohlc_1hour_aggregate") },
            argThat<Array<Any>> { this[0] == 50 && this[1] == 10 },
            any<RowMapper<VolumeStats>>()
        )
    }

    @Test
    fun `getVolumeStats should use weekly aggregate for weekly period`() {
        // Given
        whenever(jdbcTemplate.query(
            any<String>(),
            any<Array<Any>>(),
            any<RowMapper<VolumeStats>>()
        )).thenReturn(emptyList())

        // When
        analyticsRepository.getVolumeStats(
            period = "weekly",
            symbols = null,
            assetType = null,
            limit = 100,
            offset = 0
        )

        // Then
        verify(jdbcTemplate).query(
            argThat<String> { this.contains("ohlc_7day_aggregate") },
            any<Array<Any>>(),
            any<RowMapper<VolumeStats>>()
        )
    }

    @Test
    fun `getPriceTrends should execute query with correct parameters`() {
        // Given
        val symbols = listOf("GOOGL", "AMZN")
        val startDate = LocalDateTime.now().minusDays(30)
        val endDate = LocalDateTime.now()
        
        whenever(jdbcTemplate.query(
            any<String>(),
            any<Array<Any>>(),
            any<RowMapper<PriceTrends>>()
        )).thenReturn(emptyList())

        // When
        analyticsRepository.getPriceTrends(
            symbols = symbols,
            startDate = startDate,
            endDate = endDate,
            period = "daily",
            limit = 100,
            offset = 0
        )

        // Then
        verify(jdbcTemplate).query(
            argThat<String> { 
                this.contains("ohlc_1day_aggregate") &&
                this.contains("ds.symbol_code IN (?,?)")
            },
            argThat<Array<Any>> { 
                this.size == 6 &&
                this[0] == "GOOGL" && 
                this[1] == "AMZN" &&
                this[2] == startDate &&
                this[3] == endDate &&
                this[4] == 100 &&
                this[5] == 0
            },
            any<RowMapper<PriceTrends>>()
        )
    }

    @Test
    fun `getVolatilityMetrics should execute query with asset type filter`() {
        // Given
        whenever(jdbcTemplate.query(
            any<String>(),
            any<Array<Any>>(),
            any<RowMapper<VolatilityMetrics>>()
        )).thenReturn(emptyList())

        // When
        analyticsRepository.getVolatilityMetrics(
            assetType = "crypto",
            period = "daily",
            limit = 50,
            offset = 0
        )

        // Then
        verify(jdbcTemplate).query(
            argThat<String> { 
                this.contains("WHERE dat.asset_type_code = ?")
            },
            argThat<Array<Any>> { 
                this.size == 3 &&
                this[0] == "crypto" &&
                this[1] == 50 &&
                this[2] == 0
            },
            any<RowMapper<VolatilityMetrics>>()
        )
    }

    @Test
    fun `getVolatilityMetrics should execute query without filter when no asset type provided`() {
        // Given
        whenever(jdbcTemplate.query(
            any<String>(),
            any<Array<Any>>(),
            any<RowMapper<VolatilityMetrics>>()
        )).thenReturn(emptyList())

        // When
        analyticsRepository.getVolatilityMetrics(
            assetType = null,
            period = "daily",
            limit = 100,
            offset = 0
        )

        // Then
        verify(jdbcTemplate).query(
            argThat<String> { !this.contains("WHERE") },
            argThat<Array<Any>> { 
                this.size == 2 &&
                this[0] == 100 &&
                this[1] == 0
            },
            any<RowMapper<VolatilityMetrics>>()
        )
    }

    @Test
    fun `compareSymbols should execute query with all symbols`() {
        // Given
        val symbols = listOf("AAPL", "MSFT", "GOOGL")
        val startDate = LocalDateTime.now().minusDays(90)
        val endDate = LocalDateTime.now()
        
        val mockComparisons = listOf(
            SymbolComparison(
                symbol = "AAPL",
                assetType = "stock",
                priceChangePercent = BigDecimal("15.5"),
                volumeTotal = BigDecimal("50000000"),
                volatility = BigDecimal("35.2"),
                avgPrice = BigDecimal("175.50"),
                performance = ""
            )
        )

        whenever(jdbcTemplate.query(
            any<String>(),
            any<Array<Any>>(),
            any<RowMapper<SymbolComparison>>()
        )).thenReturn(mockComparisons)

        // When
        val result = analyticsRepository.compareSymbols(
            symbols = symbols,
            startDate = startDate,
            endDate = endDate,
            period = "daily"
        )

        // Then
        verify(jdbcTemplate).query(
            argThat<String> { 
                this.contains("WITH symbol_stats AS") &&
                this.contains("ds.symbol_code IN (?,?,?)")
            },
            argThat<Array<Any>> { 
                this.size == 5 &&
                this[0] == "AAPL" && 
                this[1] == "MSFT" && 
                this[2] == "GOOGL" &&
                this[3] == startDate &&
                this[4] == endDate
            },
            any<RowMapper<SymbolComparison>>()
        )
        
        assertNotNull(result)
        assertEquals(symbols, result.symbols)
        assertEquals(1, result.comparisons.size)
    }

    @Test
    fun `compareSymbols should determine performance relative to average`() {
        // Given
        val symbols = listOf("AAPL", "MSFT")
        val startDate = LocalDateTime.now().minusDays(30)
        val endDate = LocalDateTime.now()
        
        val mockComparisons = listOf(
            SymbolComparison(
                symbol = "AAPL",
                assetType = "stock",
                priceChangePercent = BigDecimal("20.0"),
                volumeTotal = BigDecimal("50000000"),
                volatility = BigDecimal("35.2"),
                avgPrice = BigDecimal("175.50"),
                performance = ""
            ),
            SymbolComparison(
                symbol = "MSFT",
                assetType = "stock",
                priceChangePercent = BigDecimal("5.0"),
                volumeTotal = BigDecimal("40000000"),
                volatility = BigDecimal("28.5"),
                avgPrice = BigDecimal("380.25"),
                performance = ""
            )
        )

        whenever(jdbcTemplate.query(
            any<String>(),
            any<Array<Any>>(),
            any<RowMapper<SymbolComparison>>()
        )).thenReturn(mockComparisons)

        // When
        val result = analyticsRepository.compareSymbols(
            symbols = symbols,
            startDate = startDate,
            endDate = endDate,
            period = "daily"
        )

        // Then
        assertNotNull(result)
        assertEquals(2, result.comparisons.size)
        // AAPL should be outperforming, MSFT underperforming (based on 20% vs 5%)
        assertEquals("outperforming", result.comparisons[0].performance)
        assertEquals("underperforming", result.comparisons[1].performance)
    }
}
