package sg.com.quantai.middleware.controllers.jpa

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sg.com.quantai.middleware.services.ReferencePriceService

@RestController
@RequestMapping("/prices")
class PriceController(private val referencePriceService: ReferencePriceService) {

    @GetMapping("/latest/{symbol}")
    fun latestPrice(@PathVariable symbol: String): ResponseEntity<Map<String, Any>> {
        val price = referencePriceService.latestPrice(symbol)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "symbol" to price.symbol,
                "price" to price.price,
                "asOf" to price.asOf.toString(),
            )
        )
    }
}
