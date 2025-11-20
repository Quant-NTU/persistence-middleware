package sg.com.quantai.middleware.controllers.jpa

import sg.com.quantai.middleware.data.jpa.TransformedStock
import sg.com.quantai.middleware.services.TransformedStockService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/stock/transformed")
class TransformedStockController(private val service: TransformedStockService) {

    @GetMapping
    fun getAllTransformedData(
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<TransformedStock>> {
        val data = service.getAllTransformedData(limit)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/{symbol}")
    fun getTransformedDataBySymbol(
        @PathVariable symbol: String,
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<TransformedStock>> {
        val data = service.getTransformedDataBySymbol(symbol, limit)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/range")
    fun getTransformedDataByTimestampRange(
        @RequestParam startTime: String,
        @RequestParam endTime: String,
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<TransformedStock>> {
        val data = service.getTransformedDataByTimestampRange(startTime, endTime, limit)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/recent")
    fun getRecentTransformedData(
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<TransformedStock>> {
        val data = service.getRecentTransformedData(limit)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/stats")
    fun getStockStats(): ResponseEntity<List<Map<String, Any>>> {
        val data = service.getAllTransformedDataLegacy()  // Use legacy method for complete stats
        val stats = mapOf(
            "total" to data.size,
            "totalVolume" to data.sumOf { it.volume },
            "avgPrice" to if (data.isNotEmpty()) data.map { it.close }.average() else 0.0,
            "totalSymbols" to data.map { it.symbol }.distinct().size
        )
        return ResponseEntity.ok(listOf(stats))
    }
}
