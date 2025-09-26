package sg.com.quantai.middleware.controllers.jpa

import sg.com.quantai.middleware.data.jpa.TransformedStock
import sg.com.quantai.middleware.services.TransformedStockService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/stock/transformed")
class TransformedStockController(private val service: TransformedStockService) {

    @GetMapping
    fun getAllTransformedData(): ResponseEntity<List<TransformedStock>> {
        val data = service.getAllTransformedData()
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/{symbol}")
    fun getTransformedDataBySymbol(
        @PathVariable symbol: String
    ): ResponseEntity<List<TransformedStock>> {
        val data = service.getTransformedDataBySymbol(symbol)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/range")
    fun getTransformedDataByTimestampRange(
        @RequestParam startTime: String,
        @RequestParam endTime: String
    ): ResponseEntity<List<TransformedStock>> {
        val data = service.getTransformedDataByTimestampRange(startTime, endTime)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/recent")
    fun getRecentTransformedData(): ResponseEntity<List<TransformedStock>> {
        val data = service.getRecentTransformedData()
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/stats")
    fun getStockStats(): ResponseEntity<List<Map<String, Any>>> {
        val data = service.getAllTransformedData()
        val stats = mapOf(
            "total" to data.size,
            "totalVolume" to data.sumOf { it.volume },
            "avgPrice" to if (data.isNotEmpty()) data.map { it.close }.average() else 0.0,
            "totalSymbols" to data.map { it.symbol }.distinct().size
        )
        return ResponseEntity.ok(listOf(stats))
    }
}
