package sg.com.quantai.middleware.controllers

import java.time.LocalDateTime
import sg.com.quantai.middleware.data.Crypto
import sg.com.quantai.middleware.repositories.mongo.CryptoRepository
import sg.com.quantai.middleware.requests.CryptoRequest
import sg.com.quantai.middleware.services.CoinRankingAPIService
import sg.com.quantai.middleware.services.POJOS.CoinDetails.CoinDetails
import sg.com.quantai.middleware.services.POJOS.CoinHistory.CoinHistory
import sg.com.quantai.middleware.services.POJOS.TopCoin.TopCoin
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus

// import kotlinx.coroutines.*
// import kotlin.random.Random

@RestController
@RequestMapping("/cryptos")
class CryptoController(
    private val cryptosRepository: CryptoRepository,
    private val coinRankingApiService: CoinRankingAPIService
) {
    // Retrieve all the cryptos
    @GetMapping
    fun getAllCryptos(): ResponseEntity<List<Crypto>> {
        try{
            val topCoin: TopCoin? = coinRankingApiService.retrieveTopCoins(100).getBody()

        if (topCoin != null && topCoin.status.equals("success")) {
            val dataObj = topCoin.data
            val coinsObj = dataObj.coins
            for (i in 0..coinsObj.size - 1) {
                val coinObj = coinsObj[i]
                val crypto_db: List<Crypto> = cryptosRepository.findByUuid(coinObj.uuid)

                if (crypto_db.isEmpty()) {

                    cryptosRepository.save(
                            Crypto(
                                    name = coinObj.name,
                                    symbol = coinObj.symbol,
                                    marketCap = coinObj.marketCap.toBigDecimal(),
                                    price = coinObj.price,
                                    iconurl = coinObj.iconUrl,
                                    change = coinObj.change,
                                    rank = coinObj.rank,
                                    volume = coinObj.`24hVolume`,
                                    uuid = coinObj.uuid,
                            )
                    )
                } else {
                    cryptosRepository.save(
                            Crypto(
                                    name = coinObj.name,
                                    symbol = coinObj.symbol,
                                    marketCap = coinObj.marketCap.toBigDecimal(),
                                    price = coinObj.price,
                                    description = crypto_db[0].description,
                                    iconurl = coinObj.iconUrl,
                                    change = coinObj.change,
                                    rank = coinObj.rank,
                                    volume = coinObj.`24hVolume`,
                                    allTimeHighPrice = crypto_db[0].allTimeHighPrice,
                                    numberOfMarkets = crypto_db[0].numberOfMarkets,
                                    numberOfExchanges = crypto_db[0].numberOfExchanges,
                                    approvedSupply = crypto_db[0].approvedSupply,
                                    totalSupply = crypto_db[0].totalSupply,
                                    circulatingSupply = crypto_db[0].circulatingSupply,
                                    links = crypto_db[0].links,
                                    coinHistory = crypto_db[0].coinHistory,
                                    uuid = coinObj.uuid,
                                    _id = crypto_db[0]._id,
                            )
                    )
                }
            }
        }
        }
        finally{
            val cryptos = cryptosRepository.findAll()
            return ResponseEntity.ok(cryptos)
        }

    }

    // Get a single crypto by id
    @GetMapping("/{uuid}")
    fun getOneCrypto(@PathVariable("uuid") uuid: String): ResponseEntity<Crypto> {
        try{
            val crypto = cryptosRepository.findOneByUuid(uuid)

            if (crypto.description == "None" || validCryptoUpdateDate(crypto.updatedDate)) {
                var coinDetails: CoinDetails?
                var coinHistory: CoinHistory?
                coinDetails = coinRankingApiService.retriveSingleCoin(uuid).getBody()
                coinHistory = coinRankingApiService.retriveSingleCoinHistory(uuid).getBody()
                if (coinDetails != null &&
                                coinHistory != null &&
                                coinDetails.status.equals("success") &&
                                coinHistory.status.equals("success")
                ) {
                    val coinDetail = coinDetails.data
                    val coinDetailObj = coinDetail.coin
                    val coinHistoryInstance = coinHistory.data
    
                    cryptosRepository.save(
                            Crypto(
                                    name = crypto.name,
                                    symbol = crypto.symbol,
                                    marketCap = crypto.marketCap,
                                    price = crypto.price,
                                    description = coinDetailObj.description,
                                    iconurl = crypto.iconurl,
                                    change = crypto.change,
                                    rank = crypto.rank,
                                    volume = crypto.volume,
                                    allTimeHighPrice = coinDetailObj.allTimeHigh.price,
                                    numberOfMarkets = coinDetailObj.numberOfMarkets,
                                    numberOfExchanges = coinDetailObj.numberOfExchanges,
                                    approvedSupply = coinDetailObj.supply.confirmed,
                                    totalSupply = coinDetailObj.supply.total,
                                    circulatingSupply = coinDetailObj.supply.circulating,
                                    links = coinDetailObj.links,
                                    coinHistory = coinHistoryInstance,
                                    uuid = crypto.uuid,
                                    _id = crypto._id,
                            )
                    )
                }
            }
        }
        finally{
            val crypto = cryptosRepository.findOneByUuid(uuid)
            return ResponseEntity.ok(crypto)
        }
        
    }

    fun validCryptoUpdateDate(cryptoUpdatedDate: LocalDateTime): Boolean {
        return cryptoUpdatedDate.plusHours(1) < LocalDateTime.now()
    }

    //Get single crypto by name
    @GetMapping("/name/{name}")
    fun getOneCryptoName(@PathVariable("name") name: String): ResponseEntity<Crypto>{
        
        if (cryptoNameExists(name)){
            val crypto = cryptosRepository.findOneByName(name)
            return ResponseEntity.ok(crypto)
        } else{
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get single crypto by symbol
    @GetMapping("/symbol/{symbol}")
    fun getOneCryptoSymbol(@PathVariable("symbol") symbol: String): ResponseEntity<Crypto>{
        if (cryptoSymbolExists(symbol)){
            val crypto = cryptosRepository.findOneBySymbol(symbol)
            return ResponseEntity.ok(crypto)
        } else{
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Get multiple cryptos by providing list of symbols
    @PostMapping("/symbol")
    fun getAllCryptoSymbol(
        @RequestBody request: CryptoRequest
    ): ResponseEntity<List<Crypto>> {
        val listOfCryptos = cryptosRepository.findBySymbolIn(request.symbols)
        return ResponseEntity(listOfCryptos, HttpStatus.OK)
    }

    fun cryptoNameExists(name: String): Boolean{
        var exists: Boolean = false
        val ListOfCryptos = cryptosRepository.findAll()
        for (c in ListOfCryptos) {
            if (c.name == name) {
                exists = true
                break
            } else {
                exists = false
            }
        }
        return exists

    }

    fun cryptoSymbolExists(symbol: String): Boolean{
        var exists: Boolean = false
        val ListOfCryptos = cryptosRepository.findAll()
        for (c in ListOfCryptos) {
            if (c.symbol == symbol) {
                exists = true
                break
            } else {
                exists = false
            }
        }
        return exists

    }
}
