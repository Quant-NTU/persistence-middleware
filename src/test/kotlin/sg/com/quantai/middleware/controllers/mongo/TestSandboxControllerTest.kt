package sg.com.quantai.middleware.controllers.mongo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import reactor.core.publisher.Mono

import sg.com.quantai.middleware.data.mongo.*
import sg.com.quantai.middleware.data.mongo.enums.PortfolioActionEnum
import sg.com.quantai.middleware.repositories.mongo.*

import java.math.BigDecimal
import java.time.LocalDate



@ExtendWith(MockitoExtension::class)
class TestSandboxControllerUnitTest {

    @Mock
    lateinit var portfolioRepository: PortfolioRepository

    @Mock
    lateinit var portfolioHistoryRepository: PortfolioHistoryRepository

    @Mock
    lateinit var usersRepository: UserRepository

    @Mock
    lateinit var strategiesRepository: StrategyRepository

    @Mock
    lateinit var sandboxWebClient: WebClient

    @InjectMocks
    lateinit var controller: TestSandboxController

    lateinit var user: User
    lateinit var portfolio: Portfolio

    @BeforeEach
    fun setUp() {
        user = User(name = "TestUser", email = "test@email.com", password = "pass", salt = "salt")
        portfolio = Portfolio(name = "MainPortfolio", description = "Test", main = true, owner = user)
    }

    @Test
    fun `should retrieve correct portfolio and history`() {
        // MOCK: Portfolio and history
        val asset1 = Crypto(name = "BTC", quantity = BigDecimal("1.0"), purchasePrice = BigDecimal("10000"), symbol = "BTC")
        val asset2 = Crypto(name = "ETH", quantity = BigDecimal("2.0"), purchasePrice = BigDecimal("2000"), symbol = "ETH")

        val history = listOf(
            PortfolioHistory(
                asset = asset1,
                action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                quantity = BigDecimal("1.0"),
                value = BigDecimal("10000"),
                portfolio = portfolio
            ),
            PortfolioHistory(
                asset = asset2,
                action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                quantity = BigDecimal("2.0"),
                value = BigDecimal("4000"),
                portfolio = portfolio
            )
        )

        `when`(portfolioRepository.findByOwnerAndMain(user, true)).thenReturn(portfolio)
        `when`(portfolioHistoryRepository.findByPortfolio(portfolio)).thenReturn(history)

        val retrievedPortfolio = portfolioRepository.findByOwnerAndMain(user, true)
        val retrievedHistory = portfolioHistoryRepository.findByPortfolio(retrievedPortfolio)

        assertNotNull(retrievedPortfolio)
        assertEquals(2, retrievedHistory.size)
        assertEquals("BTC", retrievedHistory[0].asset.name)
        assertEquals("ETH", retrievedHistory[1].asset.name)
    }

    @Test
    fun `should aggregate portfolio history correctly`() {
        // MOCK: PortfolioHistory for aggregation
        val asset1 = Crypto(name = "BTC", quantity = BigDecimal("1.0"), purchasePrice = BigDecimal("10000"), symbol = "BTC")
        val asset2 = Crypto(name = "BTC", quantity = BigDecimal("2.0"), purchasePrice = BigDecimal("10000"), symbol = "BTC")
        val asset3 = Crypto(name = "ETH", quantity = BigDecimal("3.0"), purchasePrice = BigDecimal("2000"), symbol = "ETH")

        val history = listOf(
            PortfolioHistory(
                asset = asset1,
                action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                quantity = asset1.quantity,
                value = BigDecimal("10000"),
                portfolio = portfolio
            ),
            PortfolioHistory(
                asset = asset2,
                action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                quantity = asset2.quantity,
                value = BigDecimal("20000"),
                portfolio = portfolio
            ),
            PortfolioHistory(
                asset = asset3,
                action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                quantity = asset3.quantity,
                value = BigDecimal("6000"),
                portfolio = portfolio
            ),
            PortfolioHistory(
                asset = asset1,
                action = PortfolioActionEnum.REMOVE_MANUAL_ASSET,
                quantity = asset1.quantity,
                value = BigDecimal("10000"),
                portfolio = portfolio
            ),
            PortfolioHistory(
                asset = asset3,
                action = PortfolioActionEnum.REMOVE_MANUAL_ASSET,
                quantity = asset3.quantity,
                value = BigDecimal("6000"),
                portfolio = portfolio
            )
        )

        val aggregatedAssets = controller.aggregatePortfolioHistory(history)
        
        assertEquals(2, aggregatedAssets.size)
        assertTrue(aggregatedAssets.any { it["symbol"] == "BTC" && it["quantity"] == BigDecimal("2.0") })
        assertTrue(aggregatedAssets.any { it["symbol"] == "ETH" && it["quantity"] == BigDecimal("0.0") })
        assertTrue(aggregatedAssets.any { it["symbol"] == "BTC" && it["value"] == BigDecimal("20000") })
        assertTrue(aggregatedAssets.any { it["symbol"] == "ETH" && it["value"] == BigDecimal("0") })
        assertTrue(aggregatedAssets.any { it["symbol"] == "BTC" && it["purchasePrice"] == BigDecimal("10000.00000000") })
        assertTrue(aggregatedAssets.any { it["symbol"] == "ETH" && it["purchasePrice"] == BigDecimal.ZERO })

    }

    @Test
    fun `preloadCryptoData should call correct endpoints and log success`() {
        // Mock Logger
        val mockLogger = mock(Logger::class.java)

        // Mock WebClient chain
        val mockWebClient = mock(WebClient::class.java)
        val mockRequestBodyUriSpec = mock(RequestBodyUriSpec::class.java)
        val mockRequestBodySpec = mock(RequestBodySpec::class.java)
        val mockResponseSpec = mock(ResponseSpec::class.java)

        // Stub the chain for every post call
        `when`(mockWebClient.post()).thenReturn(mockRequestBodyUriSpec)
        `when`(mockRequestBodyUriSpec.uri(anyString())).thenReturn(mockRequestBodySpec)
        `when`(mockRequestBodySpec.retrieve()).thenReturn(mockResponseSpec)
        `when`(mockResponseSpec.bodyToMono(String::class.java)).thenReturn(Mono.just("success"))

        // Call the function with mocks and parameters
        controller.preloadCryptoData(
            webClient = mockWebClient,
            cryptoBaseUrl = "http://quant-ai-persistence-etl:10070/crypto",
            cryptoSymbols = listOf("BTC", "ETH"),
            startDate = "2025-01-01",
            endDate = "2025-01-10",
            log = mockLogger
        )

        // Verify .post() called three times (2 symbols + 1 transform)
        verify(mockWebClient, times(3)).post()

        // Verify .uri() called with expected relative paths
        verify(mockRequestBodyUriSpec).uri("http://quant-ai-persistence-etl:10070/crypto/historical/store-by-date?symbol=BTC&currency=USD&startDate=2025-01-01&endDate=2025-01-10")
        verify(mockRequestBodyUriSpec).uri("http://quant-ai-persistence-etl:10070/crypto/historical/store-by-date?symbol=ETH&currency=USD&startDate=2025-01-01&endDate=2025-01-10")
        verify(mockRequestBodyUriSpec).uri("http://quant-ai-persistence-etl:10070/crypto/transform")

        // Verify log info called for success message
        verify(mockLogger).info("✅ Successfully preloaded crypto data for symbols: [BTC, ETH]")
    }

    @Test
    fun `preloadStockData should call correct endpoints and log success`() {
        // Mock Logger
        val mockLogger = mock(Logger::class.java)

        // Mock WebClient chain
        val mockWebClient = mock(WebClient::class.java)
        val mockRequestBodyUriSpec = mock(RequestBodyUriSpec::class.java)
        val mockRequestBodySpec = mock(RequestBodySpec::class.java)
        val mockResponseSpec = mock(ResponseSpec::class.java)

        // Stub the chain for every post call
        `when`(mockWebClient.post()).thenReturn(mockRequestBodyUriSpec)
        `when`(mockRequestBodyUriSpec.uri(anyString())).thenReturn(mockRequestBodySpec)
        `when`(mockRequestBodySpec.retrieve()).thenReturn(mockResponseSpec)
        `when`(mockResponseSpec.bodyToMono(String::class.java)).thenReturn(Mono.just("success"))

        // Call the function with mocks and parameters
        controller.preloadStockData(
            webClient = mockWebClient,
            stockBaseUrl = "http://quant-ai-persistence-etl:10070/stock",
            stockSymbols = listOf("AAPL", "MSFT"),
            startDate = "2025-01-01",
            endDate = "2025-01-10",
            log = mockLogger
        )

        // Verify .post() called three times (2 symbols + 1 transform)
        verify(mockWebClient, times(3)).post()

        // Verify .uri() called with expected full URLs
        verify(mockRequestBodyUriSpec).uri(
            "http://quant-ai-persistence-etl:10070/stock/historical/store-by-date?symbol=AAPL&startDate=2025-01-01&endDate=2025-01-10"
        )
        verify(mockRequestBodyUriSpec).uri(
            "http://quant-ai-persistence-etl:10070/stock/historical/store-by-date?symbol=MSFT&startDate=2025-01-01&endDate=2025-01-10"
        )
        verify(mockRequestBodyUriSpec).uri(
            "http://quant-ai-persistence-etl:10070/stock/transform"
        )

        // Verify final success log
        verify(mockLogger).info("✅ Successfully preloaded stock data for symbols: [AAPL, MSFT]")
    }


}