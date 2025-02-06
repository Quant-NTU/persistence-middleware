package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.NewStock
import sg.com.quantai.middleware.repositories.AssetStockRepository
import sg.com.quantai.middleware.requests.StockListRequest    //Might need to update this
import sg.com.quantai.middleware.requests.NewStockRequest          
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import java.math.BigDecimal

@RestController
@RequestMapping("/new-stock")
class NewStockController(
    private val stockRepository: AssetStockRepository
) {
    // Get All Stocks
    @GetMapping
    fun getAllStocks(): ResponseEntity<List<NewStock>> {
        val stocks = stockRepository.findAll()
        return ResponseEntity.ok(stocks)
    }

    // Get one stock (Updated to find by uid instead of uuid)
    @GetMapping("/{uid}")
    fun getOneStock(@PathVariable uid: String): ResponseEntity<NewStock> {
        val stock = stockRepository.findOneByUid(uid)
        return ResponseEntity.ok(stock)
    }

    // Get one stock by name
    @GetMapping("/name/{name}")
    fun getOneStockByName(@PathVariable("name") name: String): ResponseEntity<NewStock> {
        val stock = stockRepository.findOneByName(name)
        return ResponseEntity.ok(stock)
    }

    // Get one stock by symbol
    @GetMapping("/symbol/{symbol}")
    fun getOneStockBySymbol(@PathVariable("symbol") symbol: String): ResponseEntity<NewStock> {
        val stock = stockRepository.findOneBySymbol(symbol)
        return ResponseEntity.ok(stock)
    }

    // Get multiple stocks by providing list of symbols
    @PostMapping("/symbol")
    fun getAllStockSymbol(
        @RequestBody request: StockListRequest
    ): ResponseEntity<List<NewStock>> {
        val listOfStocks = stockRepository.findBySymbolIn(request.symbols)
        return ResponseEntity(listOfStocks, HttpStatus.OK)
    }

    // Add a stock
    @PostMapping("/add")
    fun addStock(
            @RequestBody request: NewStockRequest
    ) : ResponseEntity<Any> {
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

        val stock = stockRepository.save(
            NewStock(
                name = request.name,
                symbol = request.symbol,
                quantity = request.quantity,
                purchasePrice = request.purchasePrice
            )
        )
        return ResponseEntity(stock, HttpStatus.CREATED)
    }

    // Update a stock
    @PutMapping("/{uid}")
    fun updateStock(
        @PathVariable("uid") uid: String,
        @RequestBody request: NewStockRequest
    ) : ResponseEntity<Any> {
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
        
        val stock = stockRepository.findOneByUid(uid)

        val updatedStock = stock.copy(
            name = request.name ?: stock.name,
            symbol = request.symbol ?: stock.symbol,
            quantity = request.quantity?: stock.quantity,
        )

        val savedStock = stockRepository.save(updatedStock)
        return ResponseEntity.ok(savedStock)
    }

    // Delete a stock
    @DeleteMapping("/{uid}")
    fun DeleteStock(@PathVariable("uid") uid: String): ResponseEntity<Any> {
        stockRepository.deleteByUid(uid)
        return ResponseEntity.ok().body("Deleted stock ${uid}")
    }
}
