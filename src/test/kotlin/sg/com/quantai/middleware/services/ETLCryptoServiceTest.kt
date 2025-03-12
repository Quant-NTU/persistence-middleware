package sg.com.quantai.middleware.services

import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import sg.com.quantai.middleware.data.jpa.ETLCrypto
import sg.com.quantai.middleware.repositories.jpa.ETLCryptoRepository
import java.sql.Timestamp
import kotlin.test.assertEquals

class ETLCryptoServiceTest {

    private val repository: ETLCryptoRepository = mock(ETLCryptoRepository::class.java)
    private val service = ETLCryptoService(repository)

    @Test
    fun `getAllTransformedData returns all data`() {
        val mockData = listOf(ETLCrypto())
        `when`(repository.findAll()).thenReturn(mockData)

        val result = service.getAllTransformedData()

        assertEquals(mockData, result)
        verify(repository).findAll()
    }

    @Test
    fun `getTransformedDataBySymbolAndCurrency returns filtered data`() {
        val mockData = listOf(ETLCrypto())
        `when`(repository.findBySymbolAndCurrency("BTC", "USD")).thenReturn(mockData)

        val result = service.getTransformedDataBySymbolAndCurrency("BTC", "USD")

        assertEquals(mockData, result)
        verify(repository).findBySymbolAndCurrency("BTC", "USD")
    }

    @Test
    fun `getTransformedDataByTimestampRange returns data within range`() {
        val mockData = listOf(ETLCrypto())
        val startTime = Timestamp.valueOf("2024-01-01 00:00:00")
        val endTime = Timestamp.valueOf("2024-01-02 00:00:00")

        `when`(repository.findByTimestampRange(startTime, endTime)).thenReturn(mockData)

        val result = service.getTransformedDataByTimestampRange("2024-01-01 00:00:00", "2024-01-02 00:00:00")

        assertEquals(mockData, result)
        verify(repository).findByTimestampRange(startTime, endTime)
    }

    @Test
    fun `getRecentTransformedData returns recent data`() {
        val mockData = listOf(ETLCrypto())
        `when`(repository.findRecent()).thenReturn(mockData)

        val result = service.getRecentTransformedData()

        assertEquals(mockData, result)
        verify(repository).findRecent()
    }
}