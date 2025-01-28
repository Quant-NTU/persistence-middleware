package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.NewStock
import sg.com.quantai.middleware.repositories.StockRepository //Might need to update this
import sg.com.quantai.middleware.requests.StockListRequest    //Might need to update this
import sg.com.quantai.middleware.requests.StockRequest          
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus

@RestController
@RequestMapping("/stock")
class StockController(
    private val stockRepository: NewStockRepository
) {
    // Get All Stocks
    @GetMapping
    fun getAllStocks(): ResponseEntity<List<NewStock>> {
        val stocks = stockRepository.findAll()
        return ResponseEntity.ok(stocks)
    }

    // Get one stock (Updated to find by uid instead of uuid)
    @GetMapping("/{uid}")
    fun getOneStock(@PathVariable("uid") uid: String): ResponseEntity<NewStock> {
        val stock = stockRepository.findOneByUid(uid)
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
            @RequestBody request: StockRequest
    ) : ResponseEntity<NewStock> {
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
}
