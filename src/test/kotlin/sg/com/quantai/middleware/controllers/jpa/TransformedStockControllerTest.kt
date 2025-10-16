package sg.com.quantai.middleware.controllers.jpa

import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import sg.com.quantai.middleware.data.jpa.TransformedStock
import sg.com.quantai.middleware.services.TransformedStockService
import java.sql.Timestamp
import kotlin.test.assertEquals

class TransformedStockControllerTest {

    private val service: TransformedStockService = mock(TransformedStockService::class.java)
    private val controller = TransformedStockController(service)

    @Test
    fun `getAllTransformedData returns OK with data`() {
        val mockData = listOf(
            TransformedStock(
                id = 1,
                symbol = "AAPL",
                interval = "1day",
                open = 150.0,
                high = 155.0,
                low = 148.0,
                close = 152.0,
                volume = 1000000L,
                priceChange = 1.33,
                timestamp = Timestamp.valueOf("2024-01-15 00:00:00")
            )
        )
        `when`(service.getAllTransformedData(100)).thenReturn(mockData)

        val response: ResponseEntity<List<TransformedStock>> = controller.getAllTransformedData(100)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mockData, response.body)
    }

    @Test
    fun `getAllTransformedData returns NO_CONTENT when no data`() {
        `when`(service.getAllTransformedData(100)).thenReturn(emptyList())

        val response: ResponseEntity<List<TransformedStock>> = controller.getAllTransformedData(100)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `getTransformedDataBySymbol returns OK with data`() {
        val mockData = listOf(
            TransformedStock(
                id = 1,
                symbol = "AAPL",
                interval = "1day",
                open = 150.0,
                high = 155.0,
                low = 148.0,
                close = 152.0,
                volume = 1000000L,
                priceChange = 1.33,
                timestamp = Timestamp.valueOf("2024-01-15 00:00:00")
            )
        )
        `when`(service.getTransformedDataBySymbol("AAPL", 100)).thenReturn(mockData)

        val response = controller.getTransformedDataBySymbol("AAPL", 100)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mockData, response.body)
    }

    @Test
    fun `getTransformedDataBySymbol returns NO_CONTENT when no data`() {
        `when`(service.getTransformedDataBySymbol("AAPL", 100)).thenReturn(emptyList())

        val response = controller.getTransformedDataBySymbol("AAPL", 100)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `getTransformedDataByTimestampRange returns OK with data`() {
        val mockData = listOf(
            TransformedStock(
                id = 1,
                symbol = "AAPL",
                interval = "1day",
                open = 150.0,
                high = 155.0,
                low = 148.0,
                close = 152.0,
                volume = 1000000L,
                priceChange = 1.33,
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
            TransformedStock(
                id = 1,
                symbol = "AAPL",
                interval = "1day",
                open = 150.0,
                high = 155.0,
                low = 148.0,
                close = 152.0,
                volume = 1000000L,
                priceChange = 1.33,
                timestamp = Timestamp.valueOf("2024-01-15 00:00:00")
            )
        )
        `when`(service.getRecentTransformedData(100)).thenReturn(mockData)

        val response = controller.getRecentTransformedData(100)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mockData, response.body)
    }

    @Test
    fun `getStockStats returns OK with stats`() {
        val mockData = listOf(
            TransformedStock(
                id = 1,
                symbol = "AAPL",
                interval = "1day",
                open = 150.0,
                high = 155.0,
                low = 148.0,
                close = 152.0,
                volume = 1000000L,
                priceChange = 1.33,
                timestamp = Timestamp.valueOf("2024-01-15 00:00:00")
            )
        )
        `when`(service.getAllTransformedDataLegacy()).thenReturn(mockData)

        val response = controller.getStockStats()

        assertEquals(HttpStatus.OK, response.statusCode)
        val stats = response.body?.get(0)
        assertEquals(1, stats?.get("total"))
        assertEquals(1000000L, stats?.get("totalVolume"))
        assertEquals(152.0, stats?.get("avgPrice"))
        assertEquals(1, stats?.get("totalSymbols"))
    }
}

