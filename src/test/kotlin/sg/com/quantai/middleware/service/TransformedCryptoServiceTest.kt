package sg.com.quantai.middleware.services

import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import sg.com.quantai.middleware.data.TransformedCrypto
import sg.com.quantai.middleware.repositories.jpa.TransformedCryptoRepository
import java.sql.Timestamp
import kotlin.test.assertEquals

class TransformedCryptoServiceTest {

    private val repository: TransformedCryptoRepository = mock(TransformedCryptoRepository::class.java)
    private val service = TransformedCryptoService(repository)

    @Test
    fun `getAllTransformedData returns all data`() {
        val mockData = listOf(TransformedCrypto())
        `when`(repository.findAll()).thenReturn(mockData)

        val result = service.getAllTransformedData()

        assertEquals(mockData, result)
        verify(repository).findAll()
    }

    @Test
    fun `getTransformedDataBySymbolAndCurrency returns filtered data`() {
        val mockData = listOf(TransformedCrypto())
        `when`(repository.findBySymbolAndCurrency("BTC", "USD")).thenReturn(mockData)

        val result = service.getTransformedDataBySymbolAndCurrency("BTC", "USD")

        assertEquals(mockData, result)
        verify(repository).findBySymbolAndCurrency("BTC", "USD")
    }

    @Test
    fun `getTransformedDataByTimestampRange returns data within range`() {
        val mockData = listOf(TransformedCrypto())
        val startTime = Timestamp.valueOf("2024-01-01 00:00:00")
        val endTime = Timestamp.valueOf("2024-01-02 00:00:00")

        `when`(repository.findByTimestampRange(startTime, endTime)).thenReturn(mockData)

        val result = service.getTransformedDataByTimestampRange("2024-01-01 00:00:00", "2024-01-02 00:00:00")

        assertEquals(mockData, result)
        verify(repository).findByTimestampRange(startTime, endTime)
    }
}