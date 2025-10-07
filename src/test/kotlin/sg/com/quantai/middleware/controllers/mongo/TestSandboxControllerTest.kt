package sg.com.quantai.middleware.controllers.mongo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import sg.com.quantai.middleware.data.mongo.*
import sg.com.quantai.middleware.data.mongo.enums.PortfolioActionEnum
import sg.com.quantai.middleware.repositories.mongo.*
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

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
            )
        )

        val aggregatedAssets = controller.aggregatePortfolioHistory(history)

        assertEquals(2, aggregatedAssets.size)
        assertTrue(aggregatedAssets.any { it["symbol"] == "BTC" && it["quantity"] == BigDecimal("2.0") })
        assertTrue(aggregatedAssets.any { it["symbol"] == "ETH" && it["quantity"] == BigDecimal("3.0") })
    }
}
