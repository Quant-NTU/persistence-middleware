package sg.com.quantai.middleware.controllers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.services.YahooFinanceService

/**
 * Controller for Yahoo Finance market data endpoints
 * Provides real-time quotes and benchmark data for portfolio analysis
 */
@RestController
@RequestMapping("/portfolios")
class YahooFinanceController(
    private val yahooFinanceService: YahooFinanceService
) {

    private val logger: Logger = LoggerFactory.getLogger(YahooFinanceController::class.java)

    /**
     * Get benchmark data for a single symbol (SPY, QQQ, BTC)
     * 
     * @param symbol The benchmark symbol (SPY for S&P 500, QQQ for NASDAQ 100, BTC for Bitcoin)
     * @param days Number of days of historical data (default: 30)
     * @return Benchmark data with historical prices and period return
     */
    @GetMapping("/benchmark/{symbol}")
    fun getBenchmarkData(
        @PathVariable("symbol") symbol: String,
        @RequestParam("days", defaultValue = "30") days: Int
    ): ResponseEntity<Any> {
        val validBenchmarks = listOf("SPY", "QQQ", "BTC")
        val upperSymbol = symbol.uppercase()

        if (upperSymbol !in validBenchmarks) {
            return ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Invalid benchmark. Valid options: ${validBenchmarks.joinToString(", ")}"
            ))
        }

        return try {
            val benchmarkData = yahooFinanceService.fetchBenchmarkData(upperSymbol, days)
            ResponseEntity.ok(benchmarkData)
        } catch (e: Exception) {
            logger.error("Error fetching benchmark data for $symbol: ${e.message}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "message" to "Failed to fetch benchmark data: ${e.message}"
            ))
        }
    }

    /**
     * Get benchmark data for all supported benchmarks
     * 
     * @param days Number of days of historical data (default: 30)
     * @return Map of all benchmark data
     */
    @GetMapping("/benchmarks/all")
    fun getAllBenchmarks(
        @RequestParam("days", defaultValue = "30") days: Int
    ): ResponseEntity<Any> {
        return try {
            val benchmarks = listOf("SPY", "QQQ", "BTC")
            val result = benchmarks.associateWith { symbol ->
                yahooFinanceService.fetchBenchmarkData(symbol, days)
            }
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            logger.error("Error fetching all benchmarks: ${e.message}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "message" to "Failed to fetch benchmarks: ${e.message}"
            ))
        }
    }

    /**
     * Get real-time quote for a single symbol
     * 
     * @param symbol The stock/crypto symbol (e.g., AAPL, GOOGL, BTC, ETH)
     * @return Quote data with current price and daily change
     */
    @GetMapping("/quote/{symbol}")
    fun getQuote(
        @PathVariable("symbol") symbol: String
    ): ResponseEntity<Any> {
        return try {
            val quote = yahooFinanceService.fetchQuote(symbol)
            ResponseEntity.ok(quote)
        } catch (e: Exception) {
            logger.error("Error fetching quote for $symbol: ${e.message}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "message" to "Failed to fetch quote: ${e.message}"
            ))
        }
    }

    /**
     * Get real-time quotes for multiple symbols (used by Heat Map and Watchlist)
     * 
     * @param symbols Comma-separated list of symbols (e.g., AAPL,GOOGL,BTC,ETH)
     * @return Map of quotes for all requested symbols
     */
    @GetMapping("/quotes")
    fun getMultipleQuotes(
        @RequestParam("symbols") symbols: String
    ): ResponseEntity<Any> {
        val symbolList = symbols.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (symbolList.isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Please provide symbols parameter (e.g., ?symbols=AAPL,GOOGL,BTC)"
            ))
        }

        return try {
            val quotes = yahooFinanceService.fetchMultipleQuotes(symbolList)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "count" to quotes.size,
                "quotes" to quotes,
                "timestamp" to java.time.Instant.now().toString()
            ))
        } catch (e: Exception) {
            logger.error("Error fetching multiple quotes: ${e.message}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "success" to false,
                "message" to "Failed to fetch quotes: ${e.message}"
            ))
        }
    }
}

