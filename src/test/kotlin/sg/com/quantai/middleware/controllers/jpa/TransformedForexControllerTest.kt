package sg.com.quantai.middleware.controllers.jpa

import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import sg.com.quantai.middleware.data.jpa.TransformedForex
import sg.com.quantai.middleware.services.TransformedForexService
import java.sql.Timestamp
import kotlin.test.assertEquals

class TransformedForexControllerTest {

    private val service: TransformedForexService = mock(TransformedForexService::class.java)
    private val controller = TransformedForexController(service)

    @Test
    fun `getAllTransformedData returns OK with data`() {
        val mockData = listOf(
            TransformedForex(
                id = 1,
                currencyPair = "EUR/USD",
                interval = "1day",
                open = 1.0850,
                high = 1.0890,
                low = 1.0820,
                close = 1.0875,
                priceChange = 0.29,
                timestamp = Timestamp.valueOf("2024-01-15 00:00:00")
            )
        )
        `when`(service.getAllTransformedData(100)).thenReturn(mockData)

        val response: ResponseEntity<List<TransformedForex>> = controller.getAllTransformedData(100)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mockData, response.body)
    }

    @Test
    fun `getAllTransformedData returns NO_CONTENT when no data`() {
        `when`(service.getAllTransformedData(100)).thenReturn(emptyList())

        val response: ResponseEntity<List<TransformedForex>> = controller.getAllTransformedData(100)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `getTransformedDataByCurrencyPair returns OK with data`() {
        val mockData = listOf(
            TransformedForex(
                id = 1,
                currencyPair = "EUR/USD",
                interval = "1day",
                open = 1.0850,
                high = 1.0890,
                low = 1.0820,
                close = 1.0875,
                priceChange = 0.29,
                timestamp = Timestamp.valueOf("2024-01-15 00:00:00")
            )
        )
        `when`(service.getTransformedDataByCurrencyPair("EUR/USD", 100)).thenReturn(mockData)

        val response = controller.getTransformedDataByCurrencyPair("EUR/USD", 100)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mockData, response.body)
    }

    @Test
    fun `getTransformedDataByCurrencyPair returns NO_CONTENT when no data`() {
        `when`(service.getTransformedDataByCurrencyPair("EUR/USD", 100)).thenReturn(emptyList())

        val response = controller.getTransformedDataByCurrencyPair("EUR/USD", 100)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `getTransformedDataByTimestampRange returns OK with data`() {
        val mockData = listOf(
            TransformedForex(
                id = 1,
                currencyPair = "EUR/USD",
                interval = "1day",
                open = 1.0850,
                high = 1.0890,
                low = 1.0820,
                close = 1.0875,
                priceChange = 0.29,
                timestamp = Timestamp.valueOf("2024-01-15 00:00:00")
            )
        )
        `when`(service.getTransformedDataByTimestampRange("2024-01-01 00:00:00", "2024-01-31 23:59:59", 100))
            .thenReturn(mockData)

        val response = controller.getTransformedDataByTimestampRange("2024-01-01 00:00:00", "2024-01-31 23:59:59", 100)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mockData, response.body)
    }

    @Test
    fun `getRecentTransformedData returns OK with data`() {
        val mockData = listOf(
            TransformedForex(
                id = 1,
                currencyPair = "EUR/USD",
                interval = "1day",
                open = 1.0850,
                high = 1.0890,
                low = 1.0820,
                close = 1.0875,
                priceChange = 0.29,
                timestamp = Timestamp.valueOf("2024-01-15 00:00:00")
            )
        )
        `when`(service.getRecentTransformedData(100)).thenReturn(mockData)

        val response = controller.getRecentTransformedData(100)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mockData, response.body)
    }

    @Test
    fun `getForexStats returns OK with stats`() {
        val mockData = listOf(
            TransformedForex(
                id = 1,
                currencyPair = "EUR/USD",
                interval = "1day",
                open = 1.0850,
                high = 1.0890,
                low = 1.0820,
                close = 1.0875,
                priceChange = 0.29,
                timestamp = Timestamp.valueOf("2024-01-15 00:00:00")
            )
        )
        `when`(service.getAllTransformedDataLegacy()).thenReturn(mockData)

        val response = controller.getForexStats()

        assertEquals(HttpStatus.OK, response.statusCode)
        val stats = response.body?.get(0)
        assertEquals(1, stats?.get("total"))
        assertEquals(1.0875, stats?.get("avgPrice"))
        assertEquals(1, stats?.get("totalPairs"))
        assertEquals(0.29, stats?.get("avgPriceChange"))
    }
}