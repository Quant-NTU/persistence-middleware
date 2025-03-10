package sg.com.quantai.middleware.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sg.com.quantai.middleware.data.jpa.ETLStock
import sg.com.quantai.middleware.repositories.jpa.ETLStockRepository
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Service
class ETLStockService(private val repository: ETLStockRepository) {
    private val logger: Logger = LoggerFactory.getLogger(ETLStockService::class.java)

    private val dateFormatPatterns = listOf(
        "yyyy-MM-dd",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss"
    )

    fun getAllTransformedData(): List<ETLStock> =
        repository.findAll()

    fun getTransformedDataByTicker(ticker: String): List<ETLStock> =
        repository.findByTicker(ticker.uppercase())

    fun getDistinctTickers(): List<String> =
        repository.findDistinctTickers()

    fun getTransformedDataByDateRange(startDate: String, endDate: String): List<ETLStock> {
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

    fun getTransformedDataByTickerAndDateRange(
        ticker: String,
        startDate: String,
        endDate: String
    ): List<ETLStock> {
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

    fun getLatestForAllTickers(): List<ETLStock> =
        repository.findLatestForAllTickers()

    fun getRecentDataByTicker(ticker: String, limit: Int = 30): List<ETLStock> =
        repository.findRecentByTicker(ticker.uppercase(), limit)

    private fun parseDate(dateStr: String): LocalDateTime? {
        if (dateStr.isBlank()) return null

        for (pattern in dateFormatPatterns) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern)
                return LocalDateTime.parse(dateStr, formatter)
            } catch (e: DateTimeParseException) {
                // Try next pattern
            }
        }

        try {
            return LocalDate.parse(dateStr).atStartOfDay()
        } catch (e: DateTimeParseException) {
            logger.warn("Failed to parse date: $dateStr")
            return null
        }
    }
}