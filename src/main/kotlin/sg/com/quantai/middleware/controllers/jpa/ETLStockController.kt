package sg.com.quantai.middleware.controllers.jpa

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.data.jpa.ETLStock
import sg.com.quantai.middleware.services.ETLStockService

@RestController
@RequestMapping("/etl/stock")
class ETLStockController(private val service: ETLStockService) {

    @GetMapping
    fun getAllTransformedData(): ResponseEntity<List<ETLStock>> {
        val data = service.getAllTransformedData()
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/tickers")
    fun getDistinctTickers(): ResponseEntity<List<String>> {
        val tickers = service.getDistinctTickers()
        return if (tickers.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(tickers)
    }

    @GetMapping("/{ticker}")
    fun getTransformedDataByTicker(
        @PathVariable ticker: String
    ): ResponseEntity<List<ETLStock>> {
        val data = service.getTransformedDataByTicker(ticker)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/range")
    fun getTransformedDataByDateRange(
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): ResponseEntity<List<ETLStock>> {
        val data = service.getTransformedDataByDateRange(startDate, endDate)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/{ticker}/range")
    fun getTransformedDataByTickerAndDateRange(
        @PathVariable ticker: String,
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): ResponseEntity<List<ETLStock>> {
        val data = service.getTransformedDataByTickerAndDateRange(ticker, startDate, endDate)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/latest")
    fun getLatestForAllTickers(): ResponseEntity<List<ETLStock>> {
        val data = service.getLatestForAllTickers()
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/{ticker}/recent")
    fun getRecentDataByTicker(
        @PathVariable ticker: String,
        @RequestParam(defaultValue = "30") limit: Int
    ): ResponseEntity<List<ETLStock>> {
        val data = service.getRecentDataByTicker(ticker, limit)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }
}