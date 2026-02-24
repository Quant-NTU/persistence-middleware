package sg.com.quantai.middleware.controllers.jpa

import sg.com.quantai.middleware.data.jpa.TransformedForex
import sg.com.quantai.middleware.services.TransformedForexService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/forex/transformed")
class TransformedForexController(private val service: TransformedForexService) {

    @GetMapping
    fun getAllTransformedData(
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<TransformedForex>> {
        val data = service.getAllTransformedData(limit)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/{currencyPair}")
    fun getTransformedDataByCurrencyPair(
        @PathVariable currencyPair: String,
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<TransformedForex>> {
        // Original pairs come in format XXX/YYY but '/' splits the path, could not find a way to escape the '/' in the uri
        // Resorted to changing XXX/YYY to XXX|YYY when building the uri, then changing it back to XXX/YYY (how it is named in DB)
        require(Regex("^[A-Z]{3}\\|[A-Z]{3}$").matches(currencyPair)) {
            "currencyPair must come in as XXX|YYY"
        }
        val currencyPair = currencyPair.replace('|', '/') 
        val data = service.getTransformedDataByCurrencyPair(currencyPair, limit)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/range")
    fun getTransformedDataByTimestampRange(
        @RequestParam startTime: String,
        @RequestParam endTime: String,
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<TransformedForex>> {
        val data = service.getTransformedDataByTimestampRange(startTime, endTime, limit)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/recent")
    fun getRecentTransformedData(
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<TransformedForex>> {
        val data = service.getRecentTransformedData(limit)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/stats")
    fun getForexStats(): ResponseEntity<List<Map<String, Any>>> {
        val data = service.getAllTransformedDataLegacy()  // Use legacy method for complete stats
        val stats = mapOf(
            "total" to data.size,
            "avgPrice" to if (data.isNotEmpty()) data.map { it.close }.average() else 0.0,
            "totalPairs" to data.map { it.currencyPair }.distinct().size,
            "avgPriceChange" to if (data.isNotEmpty()) data.mapNotNull { it.priceChange }.average() else 0.0
        )
        return ResponseEntity.ok(listOf(stats))
    }
}
