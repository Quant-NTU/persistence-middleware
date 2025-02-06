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
    fun getForexByName(@PathVariable name: String): ResponseEntity<Forex> {
        val forex = newForexRepository.findOneByName(name)
        return if (forex != null) {
            ResponseEntity.ok(forex)
        } else {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get single forex by symbol
    @GetMapping("/symbol/{symbol}")
    fun getForexBySymbol(@PathVariable symbol: String): ResponseEntity<Forex> {
        val forex = newForexRepository.findOneBySymbol(symbol)
        return if (forex != null) {
            ResponseEntity.ok(forex)
        } else {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Create forex
    @PostMapping("/create")
    fun CreateForex(@RequestBody request: ForexRequest): ResponseEntity<Any> {
        // Validate request fields
        when {
            request.name.isNullOrBlank() -> {
                return ResponseEntity("Invalid input: Name must not be empty.", HttpStatus.BAD_REQUEST)
            }
            request.symbol.isNullOrBlank() -> {
                return ResponseEntity("Invalid input: Symbol must not be empty.", HttpStatus.BAD_REQUEST)
            }
            request.quantity <= BigDecimal.ZERO -> {
                return ResponseEntity("Invalid input: Quantity must be greater than 0.", HttpStatus.BAD_REQUEST)
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

    // Update forex
    @PutMapping("/{uid}")
    fun UpdateForex( @PathVariable("uid") uid: String, @RequestBody request: ForexRequest): ResponseEntity<Any> {
        // Validate request fields
        when {
            request.name.isNullOrBlank() -> {
                return ResponseEntity("Invalid input: Name must not be empty.", HttpStatus.BAD_REQUEST)
            }
            request.symbol.isNullOrBlank() -> {
                return ResponseEntity("Invalid input: Symbol must not be empty.", HttpStatus.BAD_REQUEST)
            }
            request.quantity <= BigDecimal.ZERO -> {
                return ResponseEntity("Invalid input: Quantity must be greater than 0.", HttpStatus.BAD_REQUEST)
            }
            request.purchasePrice <= BigDecimal.ZERO -> {
                return ResponseEntity("Invalid input: Purchase Price must be greater than 0.", HttpStatus.BAD_REQUEST)
            }
        }
        val forex = newForexRepository.findByUid(uid)

        val updatedForex = forex.copy(
            name = request.name ?: forex.name,
            symbol = request.symbol ?: forex.symbol,
            quantity = request.quantity?: forex.quantity,
        )

        val savedForex = newForexRepository.save(updatedForex)
        return ResponseEntity.ok(savedForex)
    }

    // Delete forex
    @DeleteMapping("/delete/{uid}")
    fun DeleteForex(@PathVariable("uid") uid: String): ResponseEntity<Any> {
        newForexRepository.deleteByUid(uid)
        return ResponseEntity.ok().body("Deleted forex ${uid}")
    }
}
