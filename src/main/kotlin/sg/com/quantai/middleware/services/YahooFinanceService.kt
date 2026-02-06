package sg.com.quantai.middleware.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.client.SimpleClientHttpRequestFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import jakarta.annotation.PostConstruct

@Service
class YahooFinanceService {

    private val logger: Logger = LoggerFactory.getLogger(YahooFinanceService::class.java)
    private val restTemplate: RestTemplate
    private val objectMapper = ObjectMapper()

    init {
        // Create a trust manager that trusts all certificates (for development)
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

        restTemplate = RestTemplate()
    }

    companion object {
        private const val YAHOO_FINANCE_URL = "https://query1.finance.yahoo.com/v8/finance/chart"
        
        val BENCHMARK_CONFIG = mapOf(
            "SPY" to BenchmarkInfo("SPY", "S&P 500", 500.0, 0.012, 0.0003),
            "QQQ" to BenchmarkInfo("QQQ", "NASDAQ 100", 450.0, 0.015, 0.0004),
            "BTC" to BenchmarkInfo("BTC-USD", "Bitcoin", 65000.0, 0.035, 0.0005)
        )

        val CRYPTO_SYMBOLS = mapOf(
            "BTC" to "BTC-USD",
            "ETH" to "ETH-USD",
            "SOL" to "SOL-USD",
            "ADA" to "ADA-USD",
            "DOGE" to "DOGE-USD",
            "XRP" to "XRP-USD",
            "DOT" to "DOT-USD",
            "AVAX" to "AVAX-USD",
            "MATIC" to "MATIC-USD",
            "LINK" to "LINK-USD"
        )
    }

    data class BenchmarkInfo(
        val yahooSymbol: String,
        val name: String,
        val basePrice: Double,
        val volatility: Double,
        val drift: Double
    )

    data class BenchmarkDataPoint(
        val date: String,
        val value: Double,
        val normalizedValue: Double
    )

    data class BenchmarkData(
        val symbol: String,
        val name: String,
        val data: List<BenchmarkDataPoint>,
        val periodReturn: Double,
        val currentValue: Double,
        val source: String
    )

    data class QuoteData(
        val symbol: String,
        val name: String,
        val price: Double,
        val previousClose: Double,
        val change: Double,
        val changePercent: Double,
        val currency: String,
        val marketState: String,
        val source: String
    )

    /**
     * Get the Yahoo Finance range parameter based on days
     */
    private fun getDaysToRange(days: Int): Pair<String, String> {
        return when {
            days <= 7 -> "5d" to "1d"
            days <= 30 -> "1mo" to "1d"
            days <= 90 -> "3mo" to "1d"
            days <= 180 -> "6mo" to "1d"
            days <= 365 -> "1y" to "1d"
            else -> "5y" to "1wk"
        }
    }

    /**
     * Fetch real benchmark data from Yahoo Finance
     */
    fun fetchBenchmarkData(benchmark: String, days: Int): BenchmarkData {
        val config = BENCHMARK_CONFIG[benchmark.uppercase()]
            ?: throw IllegalArgumentException("Unknown benchmark: $benchmark")

        val (range, interval) = getDaysToRange(days)
        val url = "$YAHOO_FINANCE_URL/${config.yahooSymbol}?range=$range&interval=$interval"

        logger.info("Fetching ${config.name} from Yahoo Finance: $url")

        return try {
            val headers = HttpHeaders()
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            val entity = HttpEntity<String>(headers)

            val response = restTemplate.exchange(url, HttpMethod.GET, entity, String::class.java)
            val json = objectMapper.readTree(response.body)

            val result = json.path("chart").path("result").get(0)
            val timestamps = result.path("timestamp")
            val quotes = result.path("indicators").path("quote").get(0)
            val closes = quotes.path("close")
            val meta = result.path("meta")

            val dataPoints = mutableListOf<BenchmarkDataPoint>()
            var startingPrice = 0.0
            var validStartFound = false

            for (i in 0 until timestamps.size()) {
                val closeNode = closes.get(i)
                if (closeNode == null || closeNode.isNull) continue

                val closePrice = closeNode.asDouble()
                if (!validStartFound) {
                    startingPrice = closePrice
                    validStartFound = true
                }

                val timestamp = timestamps.get(i).asLong()
                val date = java.time.Instant.ofEpochSecond(timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)

                dataPoints.add(BenchmarkDataPoint(
                    date = date,
                    value = String.format("%.2f", closePrice).toDouble(),
                    normalizedValue = String.format("%.2f", (closePrice / startingPrice) * 100).toDouble()
                ))
            }

            val finalPrice = dataPoints.lastOrNull()?.value ?: startingPrice
            val periodReturn = if (startingPrice > 0) ((finalPrice - startingPrice) / startingPrice) * 100 else 0.0

            logger.info("${config.name}: ${dataPoints.size} data points, ${String.format("%.2f", periodReturn)}% return")

            BenchmarkData(
                symbol = benchmark.uppercase(),
                name = config.name,
                data = dataPoints,
                periodReturn = String.format("%.2f", periodReturn).toDouble(),
                currentValue = String.format("%.2f", finalPrice).toDouble(),
                source = "Yahoo Finance (Real Data)"
            )
        } catch (e: Exception) {
            logger.error("Yahoo Finance error for $benchmark: ${e.message}")
            generateFallbackBenchmarkData(benchmark, days)
        }
    }

    /**
     * Generate fallback benchmark data when Yahoo Finance is unavailable
     */
    private fun generateFallbackBenchmarkData(benchmark: String, days: Int): BenchmarkData {
        val config = BENCHMARK_CONFIG[benchmark.uppercase()]
            ?: BENCHMARK_CONFIG["SPY"]!!

        val dataPoints = mutableListOf<BenchmarkDataPoint>()
        val endDate = LocalDate.now()
        var currentDate = endDate.minusDays(days.toLong())

        var currentPrice = config.basePrice
        val startingPrice = currentPrice

        var seed = benchmark.hashCode().toLong() + System.currentTimeMillis() % 1000

        while (!currentDate.isAfter(endDate)) {
            seed = (seed * 9301 + 49297) % 233280
            val random = seed.toDouble() / 233280

            val dailyReturn = config.drift + config.volatility * (random - 0.5) * 2
            currentPrice *= (1 + dailyReturn)

            dataPoints.add(BenchmarkDataPoint(
                date = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                value = String.format("%.2f", currentPrice).toDouble(),
                normalizedValue = String.format("%.2f", (currentPrice / startingPrice) * 100).toDouble()
            ))

            currentDate = currentDate.plusDays(1)
        }

        val finalPrice = dataPoints.lastOrNull()?.value ?: config.basePrice
        val periodReturn = ((finalPrice - startingPrice) / startingPrice) * 100

        logger.warn("Using fallback data for ${config.name}")

        return BenchmarkData(
            symbol = benchmark.uppercase(),
            name = config.name,
            data = dataPoints,
            periodReturn = String.format("%.2f", periodReturn).toDouble(),
            currentValue = String.format("%.2f", finalPrice).toDouble(),
            source = "Simulated (Fallback)"
        )
    }

    /**
     * Fetch real-time quote for a single symbol from Yahoo Finance
     */
    fun fetchQuote(symbol: String): QuoteData {
        // Map common crypto symbols to Yahoo format
        val yahooSymbol = CRYPTO_SYMBOLS[symbol.uppercase()] ?: symbol.uppercase()
        val url = "$YAHOO_FINANCE_URL/$yahooSymbol?range=2d&interval=1d"

        logger.info("Fetching quote for $symbol ($yahooSymbol)")

        return try {
            val headers = HttpHeaders()
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            val entity = HttpEntity<String>(headers)

            val response = restTemplate.exchange(url, HttpMethod.GET, entity, String::class.java)
            val json = objectMapper.readTree(response.body)

            val result = json.path("chart").path("result").get(0)
            val meta = result.path("meta")
            val quotes = result.path("indicators").path("quote").get(0)
            val closes = quotes.path("close")

            val currentPrice = meta.path("regularMarketPrice").asDouble(
                closes.get(closes.size() - 1)?.asDouble() ?: 0.0
            )
            val previousClose = meta.path("chartPreviousClose").asDouble(
                meta.path("previousClose").asDouble(
                    if (closes.size() > 1) closes.get(closes.size() - 2)?.asDouble() ?: currentPrice else currentPrice
                )
            )

            val change = currentPrice - previousClose
            val changePercent = if (previousClose != 0.0) (change / previousClose) * 100 else 0.0

            QuoteData(
                symbol = symbol.uppercase(),
                name = meta.path("shortName").asText(meta.path("symbol").asText(symbol)),
                price = String.format("%.2f", currentPrice).toDouble(),
                previousClose = String.format("%.2f", previousClose).toDouble(),
                change = String.format("%.2f", change).toDouble(),
                changePercent = String.format("%.2f", changePercent).toDouble(),
                currency = meta.path("currency").asText("USD"),
                marketState = meta.path("marketState").asText("CLOSED"),
                source = "Yahoo Finance"
            )
        } catch (e: Exception) {
            logger.error("Quote error for $symbol: ${e.message}")
            generateFallbackQuote(symbol)
        }
    }

    /**
     * Generate fallback quote data when Yahoo Finance is unavailable
     */
    private fun generateFallbackQuote(symbol: String): QuoteData {
        val changePercent = (Math.random() - 0.5) * 10
        val basePrice = when {
            symbol.uppercase() in listOf("BTC", "BTC-USD") -> 85000.0
            symbol.uppercase() in listOf("ETH", "ETH-USD") -> 3000.0
            else -> Math.random() * 500 + 50
        }
        val change = basePrice * (changePercent / 100)

        return QuoteData(
            symbol = symbol.uppercase(),
            name = symbol.uppercase(),
            price = String.format("%.2f", basePrice).toDouble(),
            previousClose = String.format("%.2f", basePrice - change).toDouble(),
            change = String.format("%.2f", change).toDouble(),
            changePercent = String.format("%.2f", changePercent).toDouble(),
            currency = "USD",
            marketState = "SIMULATED",
            source = "Simulated"
        )
    }

    /**
     * Fetch quotes for multiple symbols
     */
    fun fetchMultipleQuotes(symbols: List<String>): Map<String, QuoteData> {
        logger.info("Heat Map: Fetching quotes for ${symbols.size} symbols: ${symbols.joinToString(", ")}")
        return symbols.associateWith { symbol -> fetchQuote(symbol) }
    }
}

