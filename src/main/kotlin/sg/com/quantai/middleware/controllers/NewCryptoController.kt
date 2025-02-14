package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.NewCrypto
import sg.com.quantai.middleware.repositories.AssetCryptoRepository
import sg.com.quantai.middleware.requests.NewCryptoRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import java.math.BigDecimal

@RestController
@RequestMapping("/newCrypto")
class NewCryptoController(
    private val newCryptoRepository: AssetCryptoRepository,
) {

    // Retrieve all the cryptos
    @GetMapping("")
    fun getAllCryptos(): ResponseEntity<List<NewCrypto>> {
        val crypto = newCryptoRepository.findAll()
        return ResponseEntity.ok(crypto)
    }

    // Get a single crypto by uid
    @GetMapping("/{uid}")
    fun getCryptoByUid(@PathVariable uid: String): ResponseEntity<NewCrypto> {
        val crypto = newCryptoRepository.findByUid(uid)
        return if (crypto != null) {
            ResponseEntity.ok(crypto)
        } else {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get single crypto by name
    @GetMapping("/name/{name}")
    fun getCryptoByName(@PathVariable name: String): ResponseEntity<Any> {
        val crypto = newCryptoRepository.findByName(name)
        return if (crypto != null) {
            ResponseEntity.ok(crypto)
        } else {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get single crypto by symbol
    @GetMapping("/symbol/{symbol}")
    fun getCryptoBySymbol(@PathVariable symbol: String): ResponseEntity<Any> {
        val crypto = newCryptoRepository.findBySymbol(symbol)
        return if (crypto != null) {
            ResponseEntity.ok(crypto)
        } else {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get total quantity of a crypto by name
    @GetMapping("/quantity/{name}")
    fun getCryptoValueByName(@PathVariable name: String): ResponseEntity<BigDecimal> {
        val totalQuantity = newCryptoRepository.findByName(name).sumOf{ it.quantity }
        return ResponseEntity.ok(totalQuantity)
    }


    // Get total quantity of single crypto by symbol
    @GetMapping("/quantity/{symbol}")
    fun getCryptoValueBySymbol(@PathVariable symbol: String): ResponseEntity<BigDecimal> {
        val totalQuantity = newCryptoRepository.findBySymbol(symbol).sumOf{ it.quantity }
        return ResponseEntity.ok(totalQuantity)
    }

    // Create crypto
    @PostMapping("/create")
    fun CreateCrypto(@RequestBody request: NewCryptoRequest): ResponseEntity<Any> {
        // Validate request fields
        when {
            request.name.isNullOrBlank() && request.symbol.isNullOrBlank() -> {
                return ResponseEntity("Invalid input: At least  of Name or Symbol must not be empty.", HttpStatus.BAD_REQUEST)
            }
            request.quantity <= BigDecimal.ZERO -> {
                return ResponseEntity("Invalid input: Quantity must be greater than 0.", HttpStatus.BAD_REQUEST)
            }
            request.purchasePrice <= BigDecimal.ZERO -> {
                return ResponseEntity("Invalid input: Purchase Price must be greater than 0.", HttpStatus.BAD_REQUEST)
            }
        }
        
        val crypto =
            newCryptoRepository.save(
                    NewCrypto(
                        name = request.name,
                        symbol = request.symbol,
                        quantity = request.quantity,
                        purchasePrice = request.purchasePrice,
                    )
                )
        return ResponseEntity(crypto, HttpStatus.CREATED)
    }

    // Delete crypto
    @DeleteMapping("/delete/{uid}")
    fun DeleteCrypto(@PathVariable("uid") uid: String): ResponseEntity<Any> {
        newCryptoRepository.deleteByUid(uid)
        return ResponseEntity.ok().body("Deleted crypto ${uid}")
    }
}
