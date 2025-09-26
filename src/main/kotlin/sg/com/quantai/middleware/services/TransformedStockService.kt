package sg.com.quantai.middleware.services

import sg.com.quantai.middleware.data.jpa.TransformedStock
import sg.com.quantai.middleware.repositories.jpa.TransformedStockRepository
import org.springframework.stereotype.Service
import java.sql.Timestamp

@Service
class TransformedStockService(private val repository: TransformedStockRepository) {

    fun getAllTransformedData(): List<TransformedStock> = repository.findAll().toList()

    fun getTransformedDataBySymbol(symbol: String): List<TransformedStock> =
        repository.findBySymbol(symbol)

    fun getTransformedDataByTimestampRange(startTime: String, endTime: String): List<TransformedStock> {
        val startTimestamp = Timestamp.valueOf(startTime)
        val endTimestamp = Timestamp.valueOf(endTime)
        return repository.findByTimestampRange(startTimestamp, endTimestamp)
    }

    fun getRecentTransformedData(): List<TransformedStock> = repository.findRecent()

    fun getTransformedDataBySymbolOrderByTimestamp(symbol: String): List<TransformedStock> =
        repository.findBySymbolOrderByTimestampDesc(symbol)
}
