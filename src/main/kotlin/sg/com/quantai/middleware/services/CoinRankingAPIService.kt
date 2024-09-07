package sg.com.quantai.middleware.services

import sg.com.quantai.middleware.services.POJOS.CoinDetails.CoinDetails
import sg.com.quantai.middleware.services.POJOS.CoinHistory.CoinHistory
import sg.com.quantai.middleware.services.POJOS.TopCoin.TopCoin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Component
private class Headers {
    @Value("\${cryptoapi.header.rapidapi.host}") lateinit var host: String
    @Value("\${cryptoapi.header.rapidapi.key}") lateinit var key: String
}

@Service
class CoinRankingAPIService {
    @Autowired lateinit private var header: Headers

    private val baseUrl: String = "https://api.coinranking.com/v2"

    fun retrieveTopCoins(amount: Number = 100): ResponseEntity<TopCoin> {
        var restTemplate: RestTemplate = RestTemplate()

        var headers: HttpHeaders = HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set(
                "user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36"
        )
        headers.set("Accept", MediaType.APPLICATION_JSON.toString())
        headers.set("x-rapidapi-host", header.host)
        headers.set("x-rapidapi-key", header.key)
        val objects: ResponseEntity<TopCoin> =
                restTemplate.exchange(
                        baseUrl + "/coins?limit=" + amount,
                        HttpMethod.GET,
                        HttpEntity(null, headers),
                        TopCoin::class.java
                )
        return objects
    }

    fun retriveSingleCoin(coinID: String): ResponseEntity<CoinDetails> {
        var restTemplate: RestTemplate = RestTemplate()
        //println(coinID)

        var headers: HttpHeaders = HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set(
                "user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36"
        )
        headers.set("Accept", MediaType.APPLICATION_JSON.toString())
        headers.set("x-rapidapi-host", header.host)
        headers.set("x-rapidapi-key", header.key)
        val objects: ResponseEntity<CoinDetails> =
                restTemplate.exchange(
                        baseUrl + "/coin/" + coinID,
                        HttpMethod.GET,
                        HttpEntity(null, headers),
                        CoinDetails::class.java
                )
        return objects
    }

    fun retriveSingleCoinHistory(coinID: String): ResponseEntity<CoinHistory> {
        var restTemplate: RestTemplate = RestTemplate()

        var headers: HttpHeaders = HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON)
        headers.set(
                "user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36"
        )
        headers.set("Accept", MediaType.APPLICATION_JSON.toString())
        headers.set("x-rapidapi-host", header.host)
        headers.set("x-rapidapi-key", header.key)
        val objects: ResponseEntity<CoinHistory> =
                restTemplate.exchange(
                        baseUrl + "/coin/" + coinID + "/history",
                        HttpMethod.GET,
                        HttpEntity(null, headers),
                        CoinHistory::class.java
                )
        return objects
    }
}
