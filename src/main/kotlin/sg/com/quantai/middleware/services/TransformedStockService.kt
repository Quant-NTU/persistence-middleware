package sg.com.quantai.middleware.services

import sg.com.quantai.middleware.data.jpa.TransformedStock
import sg.com.quantai.middleware.repositories.jpa.TransformedStockRepository
import org.springframework.stereotype.Service
import java.sql.Timestamp

@Service
class TransformedStockService(private val repository: TransformedStockRepository) {

    companion object {
        const val DEFAULT_LIMIT = 100  // Sensible default limit
        const val MAX_LIMIT = 1000     // Maximum allowed limit
    }

    fun getAllTransformedData(limit: Int? = null): List<TransformedStock> {
        val effectiveLimit = validateLimit(limit ?: DEFAULT_LIMIT)
        return repository.findAllWithLimit(effectiveLimit)
    }

    fun getTransformedDataBySymbol(symbol: String, limit: Int? = null): List<TransformedStock> {
        val effectiveLimit = validateLimit(limit ?: DEFAULT_LIMIT)
        return repository.findBySymbolWithLimit(symbol, effectiveLimit)
    }

    fun getTransformedDataByTimestampRange(startTime: String, endTime: String, limit: Int? = null): List<TransformedStock> {
        val startTimestamp = Timestamp.valueOf(startTime)
        val endTimestamp = Timestamp.valueOf(endTime)
        val effectiveLimit = validateLimit(limit ?: DEFAULT_LIMIT)
        return repository.findByTimestampRangeWithLimit(startTimestamp, endTimestamp, effectiveLimit)
    }

    fun getRecentTransformedData(limit: Int? = null): List<TransformedStock> {
        val effectiveLimit = validateLimit(limit ?: DEFAULT_LIMIT)
        return repository.findRecentWithLimit(effectiveLimit)
    }

    fun getTransformedDataBySymbolOrderByTimestamp(symbol: String): List<TransformedStock> =
        repository.findBySymbolOrderByTimestampDesc(symbol)

    // Legacy method for backward compatibility - but now with default limit
    fun getAllTransformedDataLegacy(): List<TransformedStock> = repository.findAll().toList()

    private fun validateLimit(limit: Int): Int {
        return when {
            limit <= 0 -> DEFAULT_LIMIT
            limit > MAX_LIMIT -> MAX_LIMIT
            else -> limit
        }
    }
}
