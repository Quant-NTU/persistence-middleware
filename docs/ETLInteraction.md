
# Interaction with ETL Microservice
The `persistence-middleware` microservice interacts with the `persistence-etl` microservice, to gain access to the transformed data such as historical news and historical price,
and provides it to the front-facing user such as researchers who only have access to the `persistence-middleware` application. 
This serves as the only route for these users to gain access to this data as they do not have access to the ETL service.

## Overview
The ETL microservice only facilitates the fetching, transformation and loading of news articles at the time being, hence only transformed news data is made available to users via the middleware. 
This is faciltated via the `NewsArticleController`.


The `NewsArticleController` in the Middleware application provides an interface to retrieve transformed news articles from the ETL service. It acts as a proxy between the Middleware application and the ETL service, facilitating communication using `WebClient`.

## NewsArticleController

The `NewsArticleController` provides an endpoint to fetch all transformed news articles from the ETL service. The main functionality of this controller is encapsulated in the `/all` endpoint, which uses `WebClient` to make a request to the ETL application and retrieve the news articles.

### Base URL

- The base URL for the external ETL service is configured in the application properties and injected using `@Value("\${quantai.etl.url}")`.

### WebClient Configuration

The `WebClient` is configured with a base URL from the application properties (`quantai.etl.url`) and an increased memory size (`1 GB`) to handle potentially large datasets being retrieved from the ETL service.

## Endpoints

### Get All Transformed News Articles

#### Endpoint

- **Method**: `GET`
- **URL**: `/news_articles/all`

This endpoint retrieves all transformed news articles from the ETL service.

#### Example Request

```bash
GET /news_articles/all
```

#### Example Response (Successful)

```json
{
  "articles": [
    {
      "title": "News Title 1",
      "publishedDate": "2023-10-01",
      "description": "Description of the news article",
      "content": "Content of the news article"
    },
    {
      "title": "News Title 2",
      "publishedDate": "2023-10-02",
      "description": "Description of the news article",
      "content": "Content of the news article"
    }
  ]
}
```

#### Response Codes

- **200 OK**: Returned when the news articles are successfully retrieved from the ETL service.
- **500 INTERNAL_SERVER_ERROR**: Returned when there is an error fetching news articles from the ETL service.

### Error Handling

If there is any error when fetching the news articles from the ETL service, the controller throws a custom `NewsArticleException` with a message indicating the issue.

```kotlin
throw NewsArticleException("Error fetching news articles: ${it.message}")
```

### Configuration Properties

The following configuration properties must be set in the `application.properties` file for this controller to function properly:

- **quantai.etl.url**: The base URL of the ETL service, where the requests are made.
- **quantai.etl.endpoint.news_articles.all**: The URI endpoint to fetch all transformed news articles from the ETL service.

Example:

```properties
quantai.etl.url=http://localhost:8080
quantai.etl.endpoint.news_articles.all=/api/news_articles/transformed/all
```

## Code Overview

### WebClient Initialization

The `WebClient` is initialized in the `getWebClient()` method, which uses the base URL from the configuration:

```kotlin
private fun getWebClient(): WebClient {
    return WebClient.builder()
        .baseUrl(url)
        .codecs { it.defaultCodecs().maxInMemorySize(1024 * 1024 * 1024) } // 1 GB memory
        .build()
}
```

### Fetching Transformed News Articles

The `getTransformedNewsArticles` method makes a `GET` request to the configured ETL service endpoint and retrieves the transformed news articles. It uses `WebClient` to call the endpoint, and in case of an error, it throws a custom `NewsArticleException`.

```kotlin
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
```

---
