package sg.com.quantai.middleware.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sg.com.quantai.middleware.data.TransformedStock
import sg.com.quantai.middleware.repositories.jpa.TransformedStockRepository
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Service
class TransformedStockService(private val repository: TransformedStockRepository) {
    private val logger: Logger = LoggerFactory.getLogger(TransformedStockService::class.java)

    // Date format patterns to try when parsing dates
    private val dateFormatPatterns = listOf(
        "yyyy-MM-dd",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss"
    )

    /**
     * Get all transformed stock data
     */
    fun getAllTransformedData(): List<TransformedStock> =
        repository.findAll()

    /**
     * Get transformed data for a specific ticker
     */
    fun getTransformedDataByTicker(ticker: String): List<TransformedStock> =
        repository.findByTicker(ticker.uppercase())

    /**
     * Get distinct tickers from transformed data
     */
    fun getDistinctTickers(): List<String> =
        repository.findDistinctTickers()

    /**
     * Get transformed data within a date range
     */
    fun getTransformedDataByDateRange(startDate: String, endDate: String): List<TransformedStock> {
        try {
            val start = parseDate(startDate)
            val end = parseDate(endDate)

            if (start == null || end == null) {
                logger.error("Invalid date format: startDate=$startDate, endDate=$endDate")
                return emptyList()
            }

            return repository.findByDateRange(
                Timestamp.valueOf(start),
                Timestamp.valueOf(end)
            )
        } catch (e: Exception) {
            logger.error("Error fetching data by date range: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Get transformed data for a specific ticker within a date range
     */
    fun getTransformedDataByTickerAndDateRange(
        ticker: String,
        startDate: String,
        endDate: String
    ): List<TransformedStock> {
        try {
            val start = parseDate(startDate)
            val end = parseDate(endDate)

            if (start == null || end == null) {
                logger.error("Invalid date format: startDate=$startDate, endDate=$endDate")
                return emptyList()
            }

            return repository.findByTickerAndDateRange(
                ticker.uppercase(),
                Timestamp.valueOf(start),
                Timestamp.valueOf(end)
            )
        } catch (e: Exception) {
            logger.error("Error fetching data by ticker and date range: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Get latest data point for each ticker
     */
    fun getLatestForAllTickers(): List<TransformedStock> =
        repository.findLatestForAllTickers()

    /**
     * Get most recent data points for a specific ticker
     */
    fun getRecentDataByTicker(ticker: String, limit: Int = 30): List<TransformedStock> =
        repository.findRecentByTicker(ticker.uppercase(), limit)

    /**
     * Parse date string to LocalDateTime, trying multiple formats
     */
    private fun parseDate(dateStr: String): LocalDateTime? {
        if (dateStr.isBlank()) return null

        // First try as LocalDateTime
        for (pattern in dateFormatPatterns) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern)
                return LocalDateTime.parse(dateStr, formatter)
            } catch (e: DateTimeParseException) {
                // Try next pattern
            }
        }

        // Then try as LocalDate and convert to LocalDateTime
        try {
            return LocalDate.parse(dateStr).atStartOfDay()
        } catch (e: DateTimeParseException) {
            logger.warn("Failed to parse date: $dateStr")
            return null
        }
    }
}