package sg.com.quantai.middleware.controllers.jpa

import sg.com.quantai.middleware.data.jpa.TransformedCrypto
import sg.com.quantai.middleware.services.TransformedCryptoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/crypto/transformed")
class TransformedCryptoController(private val service: TransformedCryptoService) {

    @GetMapping
    fun getAllTransformedData(): ResponseEntity<List<TransformedCrypto>> {
        val data = service.getAllTransformedData()
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/{symbol}/{currency}")
    fun getTransformedDataBySymbolAndCurrency(
        @PathVariable symbol: String,
        @PathVariable currency: String
    ): ResponseEntity<List<TransformedCrypto>> {
        val data = service.getTransformedDataBySymbolAndCurrency(symbol, currency)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/range")
    fun getTransformedDataByTimestampRange(
        @RequestParam startTime: String,
        @RequestParam endTime: String
    ): ResponseEntity<List<TransformedCrypto>> {
        val data = service.getTransformedDataByTimestampRange(startTime, endTime)
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }

    @GetMapping("/recent")
    fun getRecentTransformedData(): ResponseEntity<List<TransformedCrypto>> {
        val data = service.getRecentTransformedData()
        return if (data.isEmpty()) ResponseEntity.noContent().build()
        else ResponseEntity.ok(data)
    }
}