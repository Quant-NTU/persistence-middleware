package monolith

import monolith.services.POJOS.TopCoin.TopCoin
import monolith.services.POJOS.TopCoin.Stats
import monolith.services.POJOS.CoinDetails.CoinDetails
import monolith.services.POJOS.CoinHistory.CoinHistory
import monolith.services.CoinRankingAPIService
import java.time.LocalDateTime
import monolith.data.GenericStatsRepository
import monolith.data.GenericStats
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.boot.configurationprocessor.json.JSONArray
import java.math.BigDecimal

@RestController
@RequestMapping("/stats")
class GenericStatsController(
    private val statsRepository: GenericStatsRepository,
    private val coinRankingApiService: CoinRankingAPIService
) {
    // Retrieve the single instance
    @GetMapping
    fun getStats(): ResponseEntity<List<GenericStats>> {
        try{
            val genericStats: TopCoin? = coinRankingApiService.retrieveTopCoins(100).getBody()
        if (genericStats != null) {
            val dataObj = genericStats.data
            val statsObj = dataObj.stats

            val curData = statsRepository.findAll()

            if (curData.isNotEmpty()){
                statsRepository.save(
                    GenericStats(
                        total = statsObj.total, 
                        totalCoins = statsObj.totalCoins, 
                        totalMarkets = statsObj.totalMarkets, 
                        totalExchanges = statsObj.totalExchanges, 
                        totalMarketCap = statsObj.totalMarketCap, 
                        total24hVolume = statsObj.total24hVolume,
                        updatedDate = LocalDateTime.now(),
                        _id = curData.first()._id,
                        uid = curData.first().uid,
                        )
                )

            } else{
                statsRepository.save(
                    GenericStats(
                        total = statsObj.total, 
                        totalCoins = statsObj.totalCoins, 
                        totalMarkets = statsObj.totalMarkets, 
                        totalExchanges = statsObj.totalExchanges, 
                        totalMarketCap = statsObj.totalMarketCap, 
                        total24hVolume = statsObj.total24hVolume
                        )
                )
            }
        }
        }
        finally{
            val stats = statsRepository.findAll()
        return ResponseEntity.ok(stats)
        }
        

        

    }
    
}
