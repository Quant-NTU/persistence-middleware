package sg.com.quantai.middleware.services

import sg.com.quantai.middleware.data.jpa.TransformedForex
import sg.com.quantai.middleware.repositories.jpa.TransformedForexRepository
import org.springframework.stereotype.Service
import java.sql.Timestamp

@Service
class TransformedForexService(private val repository: TransformedForexRepository) {

    fun getAllTransformedData(): List<TransformedForex> = repository.findAll().toList()

    fun getTransformedDataByCurrencyPair(currencyPair: String): List<TransformedForex> =
        repository.findByCurrencyPair(currencyPair)

    fun getTransformedDataByTimestampRange(startTime: String, endTime: String): List<TransformedForex> {
        val startTimestamp = Timestamp.valueOf(startTime)
        val endTimestamp = Timestamp.valueOf(endTime)
        return repository.findByTimestampRange(startTimestamp, endTimestamp)
    }

    fun getRecentTransformedData(): List<TransformedForex> = repository.findRecent()

    fun getTransformedDataByCurrencyPairOrderByTimestamp(currencyPair: String): List<TransformedForex> =
        repository.findByCurrencyPairOrderByTimestampDesc(currencyPair)
}
