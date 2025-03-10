package sg.com.quantai.middleware.controllers.jpa

import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import sg.com.quantai.middleware.data.jpa.ETLCrypto
import sg.com.quantai.middleware.services.ETLCryptoService
import kotlin.test.assertEquals

class ETLCryptoControllerTest {

    private val service: ETLCryptoService = mock(ETLCryptoService::class.java)
    private val controller = ETLCryptoController(service)

    @Test
    fun `getAllTransformedData returns OK with data`() {
        val mockData = listOf(ETLCrypto())
        `when`(service.getAllTransformedData()).thenReturn(mockData)

        val response: ResponseEntity<List<ETLCrypto>> = controller.getAllTransformedData()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mockData, response.body)
    }

    @Test
    fun `getAllTransformedData returns NO_CONTENT when no data`() {
        `when`(service.getAllTransformedData()).thenReturn(emptyList())

        val response: ResponseEntity<List<ETLCrypto>> = controller.getAllTransformedData()

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `getTransformedDataBySymbolAndCurrency returns OK with data`() {
        val mockData = listOf(ETLCrypto())
        `when`(service.getTransformedDataBySymbolAndCurrency("BTC", "USD")).thenReturn(mockData)

        val response = controller.getTransformedDataBySymbolAndCurrency("BTC", "USD")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mockData, response.body)
    }

    @Test
    fun `getTransformedDataBySymbolAndCurrency returns NO_CONTENT when no data`() {
        `when`(service.getTransformedDataBySymbolAndCurrency("BTC", "USD")).thenReturn(emptyList())

        val response = controller.getTransformedDataBySymbolAndCurrency("BTC", "USD")

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `getTransformedDataByTimestampRange returns OK with data`() {
        val mockData = listOf(ETLCrypto())
        `when`(service.getTransformedDataByTimestampRange("2024-01-01 00:00:00", "2024-01-02 00:00:00"))
            .thenReturn(mockData)

        val response = controller.getTransformedDataByTimestampRange("2024-01-01 00:00:00", "2024-01-02 00:00:00")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mockData, response.body)
    }

    @Test
    fun `getTransformedDataByTimestampRange returns NO_CONTENT when no data`() {
        `when`(service.getTransformedDataByTimestampRange("2024-01-01 00:00:00", "2024-01-02 00:00:00"))
            .thenReturn(emptyList())

        val response = controller.getTransformedDataByTimestampRange("2024-01-01 00:00:00", "2024-01-02 00:00:00")

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `getRecentTransformedData returns OK with data`() {
        val mockData = listOf(ETLCrypto())
        `when`(service.getRecentTransformedData()).thenReturn(mockData)

        val response = controller.getRecentTransformedData()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mockData, response.body)
    }

    @Test
    fun `getRecentTransformedData returns NO_CONTENT when no data`() {
        `when`(service.getRecentTransformedData()).thenReturn(emptyList())

        val response = controller.getRecentTransformedData()

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }
}