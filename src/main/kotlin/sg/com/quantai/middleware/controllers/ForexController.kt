package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.Forex
import sg.com.quantai.middleware.repositories.AssetForexRepository
import sg.com.quantai.middleware.requests.ForexRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import java.math.BigDecimal

@RestController
@RequestMapping("/Forex")
class ForexController(
    private val newForexRepository: AssetForexRepository,
) {

    // Retrieve all the forexs
    @GetMapping("")
    fun getAllForexs(): ResponseEntity<List<Forex>> {
        val forex = newForexRepository.findAll()
        return ResponseEntity.ok(forex)
    }

    // Get a single forex by uid
    @GetMapping("/{uid}")
    fun getForexByUid(@PathVariable uid: String): ResponseEntity<Forex> {
        val forex = newForexRepository.findByUid(uid)
        if (forex != null) {
            return ResponseEntity.ok(forex)
        } else {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get single forex by name
    @GetMapping("/name/{name}")
    fun getForexByName(@PathVariable name: String): ResponseEntity<Any> {
        val forex = newForexRepository.findByName(name)
        return if (forex != null) {
            ResponseEntity.ok(forex)
        } else {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get single forex by symbol
    @GetMapping("/symbol/{symbol}")
    fun getForexBySymbol(@PathVariable symbol: String): ResponseEntity<Any> {
        val forex = newForexRepository.findBySymbol(symbol)
        return if (forex != null) {
            ResponseEntity.ok(forex)
        } else {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get total quantity of a forex by name
    @GetMapping("/quantity/{name}")
    fun getForexValueByName(@PathVariable name: String): ResponseEntity<BigDecimal> {
        val totalQuantity = newForexRepository.findByName(name).sumOf{ it.quantity }
        return ResponseEntity.ok(totalQuantity)
    }


    // Get total quantity of single forex by symbol
    @GetMapping("/quantity/{symbol}")
    fun getForexValueBySymbol(@PathVariable symbol: String): ResponseEntity<BigDecimal> {
        val totalQuantity = newForexRepository.findBySymbol(symbol).sumOf{ it.quantity }
        return ResponseEntity.ok(totalQuantity)
    }

    // Create forex
    @PostMapping("/create")
    fun CreateForex(@RequestBody request: ForexRequest): ResponseEntity<Any> {
        // Validate request fields
        when {
            request.name.isNullOrBlank() && request.symbol.isNullOrBlank() -> {
                return ResponseEntity("Invalid input: At least one of Name or Symbol must not be empty.", HttpStatus.BAD_REQUEST)
            }
            request.quantity < BigDecimal.ZERO -> {
                return ResponseEntity("Invalid input: Quantity cannot be negative.", HttpStatus.BAD_REQUEST)
            }
            request.purchasePrice <= BigDecimal.ZERO -> {
                return ResponseEntity("Invalid input: Purchase Price must be greater than 0.", HttpStatus.BAD_REQUEST)
            }
        }
        
        val forex =
            newForexRepository.save(
                    Forex(
                        name = request.name,
                        symbol = request.symbol,
                        quantity = request.quantity,
                        purchasePrice = request.purchasePrice,
                    )
                )
        return ResponseEntity(forex, HttpStatus.CREATED)
    }

    // Delete forex
    @DeleteMapping("/delete/{uid}")
    fun DeleteForex(@PathVariable("uid") uid: String): ResponseEntity<Any> {
        newForexRepository.deleteByUid(uid)
        return ResponseEntity.ok().body("Deleted forex ${uid}")
    }
}
