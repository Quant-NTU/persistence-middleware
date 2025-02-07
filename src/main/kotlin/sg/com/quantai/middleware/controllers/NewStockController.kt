package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.NewStock
import sg.com.quantai.middleware.repositories.AssetStockRepository
import sg.com.quantai.middleware.requests.NewStockRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import java.math.BigDecimal

@RestController
@RequestMapping("/NewStock")
class NewStockController(
    private val newStockRepository: AssetStockRepository,
) {

    // Retrieve all the stocks
    @GetMapping("")
    fun getAllStocks(): ResponseEntity<List<NewStock>> {
        val stock = newStockRepository.findAll()
        return ResponseEntity.ok(stock)
    }

    // Get a single stock by uid
    @GetMapping("/{uid}")
    fun getStockByUid(@PathVariable uid: String): ResponseEntity<NewStock> {
        val stock = newStockRepository.findByUid(uid)
        if (stock != null) {
            return ResponseEntity.ok(stock)
        } else {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get single stock by name
    @GetMapping("/name/{name}")
    fun getStockByName(@PathVariable name: String): ResponseEntity<Any> {
        val stock = newStockRepository.findByName(name)
        return if (stock != null) {
            ResponseEntity.ok(stock)
        } else {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get single stock by symbol
    @GetMapping("/symbol/{symbol}")
    fun getStockBySymbol(@PathVariable symbol: String): ResponseEntity<Any> {
        val stock = newStockRepository.findBySymbol(symbol)
        return if (stock != null) {
            ResponseEntity.ok(stock)
        } else {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get total quantity of a stock by name
    @GetMapping("/quantity/{name}")
    fun getStockValueByName(@PathVariable name: String): ResponseEntity<BigDecimal> {
        val totalQuantity = newStockRepository.findByName(name).sumOf{ it.quantity }
        return ResponseEntity.ok(totalQuantity)
    }


    // Get total quantity of single stock by symbol
    @GetMapping("/quantity/{symbol}")
    fun getStockValueBySymbol(@PathVariable symbol: String): ResponseEntity<BigDecimal> {
        val totalQuantity = newStockRepository.findBySymbol(symbol).sumOf{ it.quantity }
        return ResponseEntity.ok(totalQuantity)
    }

    // Create stock
    @PostMapping("/create")
    fun CreateStock(@RequestBody request: NewStockRequest): ResponseEntity<Any> {
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
        
        val stock =
            newStockRepository.save(
                    NewStock(
                        name = request.name,
                        symbol = request.symbol,
                        quantity = request.quantity,
                        purchasePrice = request.purchasePrice,
                    )
                )
        return ResponseEntity(stock, HttpStatus.CREATED)
    }

    // Update stock
    @PutMapping("/{uid}")
    fun UpdateStock( @PathVariable("uid") uid: String, @RequestBody request: NewStockRequest): ResponseEntity<Any> {
        // Validate request fields
        when {
            request.name.isNullOrBlank() && request.symbol.isNullOrBlank() -> {
                return ResponseEntity("Invalid input: At least one of Name or Symbol must not be empty.", HttpStatus.BAD_REQUEST)
            }
            request.quantity <= BigDecimal.ZERO -> {
                return ResponseEntity("Invalid input: Quantity must be greater than 0.", HttpStatus.BAD_REQUEST)
            }
            request.purchasePrice <= BigDecimal.ZERO -> {
                return ResponseEntity("Invalid input: Purchase Price must be greater than 0.", HttpStatus.BAD_REQUEST)
            }
        }
        val stock = newStockRepository.findByUid(uid)

        val updatedStock = stock.copy(
            name = request.name ?: stock.name,
            symbol = request.symbol ?: stock.symbol,
            quantity = request.quantity?: stock.quantity,
        )

        val savedStock = newStockRepository.save(updatedStock)
        return ResponseEntity.ok(savedStock)
    }

    // Delete stock
    @DeleteMapping("/delete/{uid}")
    fun DeleteStock(@PathVariable("uid") uid: String): ResponseEntity<Any> {
        newStockRepository.deleteByUid(uid)
        return ResponseEntity.ok().body("Deleted stock ${uid}")
    }
}
