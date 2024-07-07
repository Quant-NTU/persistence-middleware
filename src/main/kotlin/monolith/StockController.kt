package monolith

import monolith.data.Crypto
import monolith.data.Stock
import monolith.data.StockRepository
import monolith.request.CryptoRequest
import monolith.request.StockListRequest
import monolith.request.StockRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus

@RestController
@RequestMapping("/stock")
class StockController(
        private val stockRepository: StockRepository
) {
    // Get All Stocks
    @GetMapping
    fun getAllStocks(): ResponseEntity<List<Stock>> {
        val stocks = stockRepository.findAll()
        return ResponseEntity.ok(stocks)
    }

    // Get one stock
    @GetMapping("/{uuid}")
    fun getOneStock(@PathVariable("uuid") uuid: String): ResponseEntity<Stock> {
        val stock = stockRepository.findOneByUuid(uuid)
        return ResponseEntity.ok(stock)
    }

    // Get one stock by symbol
    @GetMapping("/symbol/{symbol}")
    fun getOneStockBySymbol(@PathVariable("symbol") symbol: String): ResponseEntity<Stock> {
        val stock = stockRepository.findOneBySymbol(symbol)
        return ResponseEntity.ok(stock)
    }

    // Get multiple cryptos by providing list of symbols
    @PostMapping("/symbol")
    fun getAllStockSymbol(
        @RequestBody request: StockListRequest
    ): ResponseEntity<List<Stock>> {
        val listOfStocks = stockRepository.findBySymbolIn(request.symbols)
        return ResponseEntity(listOfStocks, HttpStatus.OK)
    }

    // Add a stock
    @PostMapping("/add")
    fun addStock(
            @RequestBody request: StockRequest
    ) : ResponseEntity<Stock> {
        val stock = stockRepository.save(
            Stock(
                name = request.name,
                symbol = request.symbol,
                marketCap = request.marketCap,
                price = request.price,
                description = request.description,
                change = request.change,
                volume = request.volume
            )
        )
        return ResponseEntity(stock, HttpStatus.CREATED)
    }
}