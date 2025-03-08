package sg.com.quantai.middleware.controllers

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.data.TransformedStock
import sg.com.quantai.middleware.services.TransformedStockService

@RestController
@RequestMapping("/stock/transformed")
class TransformedStockController(private val service: TransformedStockService) {

    /**
     * Get all transformed stock data
     */
    @GetMapping
    fun getAllTransformedData(): ResponseEntity<List<TransformedStock>> {
        val data = service.getAllTransformedData()
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    /**
     * Get all distinct tickers
     */
    @GetMapping("/tickers")
    fun getDistinctTickers(): ResponseEntity<List<String>> {
        val tickers = service.getDistinctTickers()
        return if (tickers.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(tickers)
    }

    /**
     * Get transformed data for a specific ticker
     */
    @GetMapping("/{ticker}")
    fun getTransformedDataByTicker(
        @PathVariable ticker: String
    ): ResponseEntity<List<TransformedStock>> {
        val data = service.getTransformedDataByTicker(ticker)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    /**
     * Get transformed data within a date range
     */
    @GetMapping("/range")
    fun getTransformedDataByDateRange(
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): ResponseEntity<List<TransformedStock>> {
        val data = service.getTransformedDataByDateRange(startDate, endDate)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    /**
     * Get transformed data for a specific ticker within a date range
     */
    @GetMapping("/{ticker}/range")
    fun getTransformedDataByTickerAndDateRange(
        @PathVariable ticker: String,
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): ResponseEntity<List<TransformedStock>> {
        val data = service.getTransformedDataByTickerAndDateRange(ticker, startDate, endDate)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    /**
     * Get latest data point for each ticker
     */
    @GetMapping("/latest")
    fun getLatestForAllTickers(): ResponseEntity<List<TransformedStock>> {
        val data = service.getLatestForAllTickers()
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    /**
     * Get most recent data points for a specific ticker
     */
    @GetMapping("/{ticker}/recent")
    fun getRecentDataByTicker(
        @PathVariable ticker: String,
        @RequestParam(defaultValue = "30") limit: Int
    ): ResponseEntity<List<TransformedStock>> {
        val data = service.getRecentDataByTicker(ticker, limit)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }
}