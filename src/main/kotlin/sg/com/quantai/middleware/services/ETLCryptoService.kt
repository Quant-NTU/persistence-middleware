package sg.com.quantai.middleware.services

import sg.com.quantai.middleware.data.jpa.ETLCrypto
import sg.com.quantai.middleware.repositories.jpa.ETLCryptoRepository
import org.springframework.stereotype.Service
import java.sql.Timestamp

@Service
class ETLCryptoService(private val repository: ETLCryptoRepository) {

    fun getAllTransformedData(): List<ETLCrypto> = repository.findAll().toList()

    fun getTransformedDataBySymbolAndCurrency(symbol: String, currency: String): List<ETLCrypto> =
        repository.findBySymbolAndCurrency(symbol, currency)

    fun getTransformedDataByTimestampRange(startTime: String, endTime: String): List<ETLCrypto> {
        val startTimestamp = Timestamp.valueOf(startTime)
        val endTimestamp = Timestamp.valueOf(endTime)
        return repository.findByTimestampRange(startTimestamp, endTimestamp)
    }

    fun getRecentTransformedData(): List<ETLCrypto> = repository.findRecent()
}