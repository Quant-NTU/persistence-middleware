package sg.com.quantai.middleware.controllers

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import org.springframework.beans.factory.annotation.Value
import sg.com.quantai.middleware.exceptions.NewsHeadlinesException

@RestController
@RequestMapping("/news_articles")
class NewsArticlesController {

    @Value("\${quantai.etl.url}")
    private lateinit var etlBaseUrl: String

    private fun getWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(etlBaseUrl)
            .codecs { it.defaultCodecs().maxInMemorySize(1024 * 1024 * 1024) }
            .build()
    }

    @GetMapping("/all")
    fun getTransformedNewsArticles(): Mono<ResponseEntity<Any>> {
        return getWebClient()
            .get()
            .uri("/news_articles/api/all")
            .retrieve()
            .bodyToMono(Any::class.java)
            .map { articles ->
                ResponseEntity.ok(articles)
            }
            .onErrorResume {
                throw NewsHeadlinesException("Error fetching news articles: ${it.message}")
            }
    }
}