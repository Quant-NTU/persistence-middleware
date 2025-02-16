package sg.com.quantai.middleware.controllers.mongo

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import org.springframework.beans.factory.annotation.Value
import sg.com.quantai.middleware.exceptions.NewsArticleException

@RestController
@RequestMapping("/news_articles")
class NewsArticleController {

    @Value("\${quantai.etl.url}")
    private lateinit var url: String
    @Value("\${quantai.etl.endpoint.news_articles.all}")
    private lateinit var allArticlesUri: String

    private fun getWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(url)
            .codecs { it.defaultCodecs().maxInMemorySize(1024 * 1024 * 1024) } // 1 GB
            .build()
    }

    @GetMapping("/all")
    fun getTransformedNewsArticles(): Mono<ResponseEntity<Any>> {
        return getWebClient()
            .get()
            .uri(allArticlesUri)
            .retrieve()
            .bodyToMono(Any::class.java)
            .map { articles ->
                ResponseEntity.ok(articles)
            }
            .onErrorResume {
                throw NewsArticleException("Error fetching news articles: ${it.message}")
            }
    }
}
