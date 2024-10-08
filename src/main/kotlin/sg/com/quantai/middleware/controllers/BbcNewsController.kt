package sg.com.quantai.middleware.controllers

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import org.springframework.beans.factory.annotation.Value

@RestController
@RequestMapping("/bbcnews")
class BbcNewsController {

    @Value("\${quantai.etl.url}")
    private lateinit var etlServiceBaseUrl: String

    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl(etlServiceBaseUrl)
            .codecs { it.defaultCodecs().maxInMemorySize(1024 * 1024 * 1024) }
            .build()
    }

    @GetMapping("/transformed")
    fun getTransformedNewsArticles(): Mono<ResponseEntity<Any>> {
        val etlServiceUrl = "/api/transformed-bbc-news/all"

        return webClient
            .get()
            .uri(etlServiceUrl)
            .retrieve()
            .bodyToMono(Any::class.java) // Handle dynamic response type
            .map { articles ->
                ResponseEntity.ok(articles)
            }
            .onErrorResume {
                Mono.just(ResponseEntity.status(500).body("Error fetching bbc news articles: ${it.message}"))
            }
    }
}
