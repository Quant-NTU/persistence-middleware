package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.Crypto
import sg.com.quantai.middleware.repositories.CryptoRepository
import sg.com.quantai.middleware.requests.CryptoRequest
import sg.com.quantai.middleware.services.CoinRankingAPIService
import sg.com.quantai.middleware.services.POJOS.TopCoin.TopCoin
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import java.math.BigDecimal

@RestController
@RequestMapping("/new-cryptos")
class NewCryptoController(
    private val newCryptoRepository: NewCryptoRepository,
    private val coinRankingApiService: CoinRankingAPIService
) {

    // Retrieve all the cryptos
    @GetMapping
    fun getAllCryptos(): ResponseEntity<List<NewCrypto>> {
        try {
            val topCoin: TopCoin? = coinRankingApiService.retrieveTopCoins(100).body

            topCoin?.data?.coins?.forEach { coin ->
                val existingCrypto = newCryptoRepository.findByUid(coin.uuid)
                val crypto = NewCrypto(
                    name = coin.name,
                    symbol = coin.symbol,
                    quantity = BigDecimal.ZERO, // Placeholder, update with real data if available
                    purchasePrice = coin.price,
                    uid = coin.uuid,
                    _id = existingCrypto?._id ?: ObjectId.get()
                )
                newCryptoRepository.save(crypto)
            }
        } finally {
            val cryptos = newCryptoRepository.findAll()
            return ResponseEntity.ok(cryptos)
        }
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
    fun getCryptoByName(@PathVariable name: String): ResponseEntity<NewCrypto> {
        val crypto = newCryptoRepository.findOneByName(name)
        return if (crypto != null) {
            ResponseEntity.ok(crypto)
        } else {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get single crypto by symbol
    @GetMapping("/symbol/{symbol}")
    fun getCryptoBySymbol(@PathVariable symbol: String): ResponseEntity<NewCrypto> {
        val crypto = newCryptoRepository.findOneBySymbol(symbol)
        return if (crypto != null) {
            ResponseEntity.ok(crypto)
        } else {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get multiple cryptos by providing list of symbols
    @PostMapping("/symbols")
    fun getCryptosBySymbols(@RequestBody request: CryptoRequest): ResponseEntity<List<NewCrypto>> {
        val cryptos = newCryptoRepository.findBySymbolIn(request.symbols)
        return ResponseEntity.ok(cryptos)
    }
}
