package sg.com.quantai.middleware.services

import sg.com.quantai.middleware.data.jpa.TransformedForex
import sg.com.quantai.middleware.repositories.jpa.TransformedForexRepository
import org.springframework.stereotype.Service
import java.sql.Timestamp

@Service
class TransformedForexService(private val repository: TransformedForexRepository) {

    companion object {
        const val DEFAULT_LIMIT = 100  // Sensible default limit
        const val MAX_LIMIT = 1000     // Maximum allowed limit
    }

    fun getAllTransformedData(limit: Int? = null): List<TransformedForex> {
        val effectiveLimit = validateLimit(limit ?: DEFAULT_LIMIT)
        return repository.findAllWithLimit(effectiveLimit)
    }

    fun getTransformedDataByCurrencyPair(currencyPair: String, limit: Int? = null): List<TransformedForex> {
        val effectiveLimit = validateLimit(limit ?: DEFAULT_LIMIT)
        return repository.findByCurrencyPairWithLimit(currencyPair, effectiveLimit)
    }

    fun getTransformedDataByTimestampRange(startTime: String, endTime: String, limit: Int? = null): List<TransformedForex> {
        val startTimestamp = Timestamp.valueOf(startTime)
        val endTimestamp = Timestamp.valueOf(endTime)
        val effectiveLimit = validateLimit(limit ?: DEFAULT_LIMIT)
        return repository.findByTimestampRangeWithLimit(startTimestamp, endTimestamp, effectiveLimit)
    }

    fun getRecentTransformedData(limit: Int? = null): List<TransformedForex> {
        val effectiveLimit = validateLimit(limit ?: DEFAULT_LIMIT)
        return repository.findRecentWithLimit(effectiveLimit)
    }

    fun getTransformedDataByCurrencyPairOrderByTimestamp(currencyPair: String): List<TransformedForex> =
        repository.findByCurrencyPairOrderByTimestampDesc(currencyPair)

    // Legacy method for backward compatibility - but now with default limit
    fun getAllTransformedDataLegacy(): List<TransformedForex> = repository.findAll().toList()

    private fun validateLimit(limit: Int): Int {
        return when {
            limit <= 0 -> DEFAULT_LIMIT
            limit > MAX_LIMIT -> MAX_LIMIT
            else -> limit
        }
    }
}
