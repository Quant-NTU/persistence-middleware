package sg.com.quantai.middleware.services

import sg.com.quantai.middleware.data.TransformedCrypto
import sg.com.quantai.middleware.repositories.jpa.TransformedCryptoRepository
import org.springframework.stereotype.Service
import java.sql.Timestamp

@Service
class TransformedCryptoService(private val repository: TransformedCryptoRepository) {

    fun getAllTransformedData(): List<TransformedCrypto> = repository.findAll().toList()

    fun getTransformedDataBySymbolAndCurrency(symbol: String, currency: String): List<TransformedCrypto> =
        repository.findBySymbolAndCurrency(symbol, currency)

    fun getTransformedDataByTimestampRange(startTime: String, endTime: String): List<TransformedCrypto> {
        val startTimestamp = Timestamp.valueOf(startTime)
        val endTimestamp = Timestamp.valueOf(endTime)
        return repository.findByTimestampRange(startTimestamp, endTimestamp)
    }
}
