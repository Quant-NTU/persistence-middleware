
import org.springframework.data.mongodb.core.MongoTemplate
import sg.com.quantai.middleware.repositories.mongo.*
import sg.com.quantai.middleware.data.mongo.*
import sg.com.quantai.middleware.requests.PortfolioRequest
import sg.com.quantai.middleware.requests.assets.CryptoRequest
import sg.com.quantai.middleware.data.mongo.enums.PortfolioActionEnum
import sg.com.quantai.middleware.configs.JwtService
import sg.com.quantai.middleware.MiddlewareApplication

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.mindrot.jbcrypt.BCrypt

import java.math.BigDecimal

@SpringBootTest(
    classes = [MiddlewareApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("rbac-test")
class PortfolioControllerTest @Autowired constructor(
    private val restTemplate: TestRestTemplate,
    private val portfolioRepository: PortfolioRepository,
    private val portfolioHistoryRepository: PortfolioHistoryRepository,
    private val cryptoRepository: CryptoRepository,
    private val stockRepository: StockRepository,
    private val forexRepository: ForexRepository,
    private val mongoTemplate: MongoTemplate,
    private val userRepository: UserRepository,
    private val jwtService: JwtService
) {

    @LocalServerPort
    protected var port: Int = 0

    @BeforeEach
    fun setUp() {
        check(mongoTemplate.db.name == "quantai_rbac_automated_test") {
            "REFUSING destructive cleanup: unsafe MongoDB database ${mongoTemplate.db.name}"
        }

        portfolioHistoryRepository.deleteAll()
        portfolioRepository.deleteAll()
        cryptoRepository.deleteAll()
        stockRepository.deleteAll()
        forexRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun getRootUrl(): String =
        "http://localhost:$port/portfolios"

    // ------------------------------------------------------------
    // JWT AUTHENTICATION HELPERS
    // ------------------------------------------------------------

    private fun authHeaders(user: User): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(jwtService.generateToken(user.uid))
        }

    private fun <T : Any> authenticatedRequest(
        user: User,
        method: HttpMethod,
        path: String,
        responseType: Class<T>,
        body: Any? = null
    ) = restTemplate.exchange(
        getRootUrl() + path,
        method,
        HttpEntity(body, authHeaders(user)),
        responseType
    )

    // ------------------------------------------------------------
    // TEST DATA HELPERS
    // ------------------------------------------------------------

    private fun saveOnePortfolio(
        name: String = "Test",
        description: String = "Test2",
        main: Boolean = false,
        owner: User = userRepository.save(
            User(
                name = "Name",
                email = "Email",
                password = "Password",
                salt = "Salt",
                role = "Trader"
            )
        )
    ): Portfolio =
        portfolioRepository.save(
            Portfolio(
                name = name,
                description = description,
                main = main,
                owner = owner
            )
        )

    private fun preparePortfolioRequest(
        name: String = "TestName",
        description: String = "test description"
    ) = PortfolioRequest(
        name = name,
        description = description
    )

    private fun prepareCryptoRequest(
        portfolio_uid: String = "TestC",
        name: String = "TestAsset",
        quantity: BigDecimal = BigDecimal.ONE,
        purchasePrice: BigDecimal = BigDecimal(3),
        symbol: String = "TST",
        action: String = "Add"
    ) = CryptoRequest(
        portfolio_uid = portfolio_uid,
        name = name,
        quantity = quantity,
        purchasePrice = purchasePrice,
        symbol = symbol,
        action = action
    )

    private fun saveOneAsset(
        name: String = "TestAsset",
        quantity: BigDecimal = BigDecimal(2),
        purchasePrice: BigDecimal = BigDecimal(3),
        symbol: String = "TST",
        portfolio: Portfolio = saveOnePortfolio()
    ) {
        val crypto = cryptoRepository.save(
            Crypto(
                name = name,
                quantity = quantity,
                purchasePrice = purchasePrice,
                symbol = symbol
            )
        )

        portfolioHistoryRepository.save(
            PortfolioHistory(
                asset = crypto,
                action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                quantity = quantity,
                value = quantity * purchasePrice,
                portfolio = portfolio
            )
        )
    }

    private fun hashAndSaltPassword(
        plainTextPassword: String,
        salt: String? = null
    ): Pair<String, String> {
        val generatedSalt = salt ?: BCrypt.gensalt()
        val hashedPassword = BCrypt.hashpw(
            plainTextPassword,
            generatedSalt
        )

        return Pair(hashedPassword, generatedSalt)
    }


    // ============================================================
    // REGRESSION: MULTIPLE HISTORY RECORDS FOR THE SAME ASSET
    // ============================================================

    @Test
    fun `should allow multiple portfolio history records for the same asset`() {
        val portfolio = saveOnePortfolio()

        val crypto = cryptoRepository.save(
            Crypto(
                name = "Bitcoin",
                quantity = BigDecimal.ONE,
                purchasePrice = BigDecimal("50000"),
                symbol = "BTC"
            )
        )

        val first = portfolioHistoryRepository.save(
            PortfolioHistory(
                asset = crypto,
                action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                quantity = BigDecimal.ONE,
                value = BigDecimal("50000"),
                portfolio = portfolio
            )
        )

        val second = portfolioHistoryRepository.save(
            PortfolioHistory(
                asset = crypto,
                action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                quantity = BigDecimal.ONE,
                value = BigDecimal("50000"),
                portfolio = portfolio
            )
        )

        assertNotEquals(first.uid, second.uid)
        assertEquals(first.asset.uid, second.asset.uid)

        val saved = portfolioHistoryRepository.findAll()
        assertEquals(2, saved.size)
        assertEquals(2, saved.map { it.uid }.distinct().size)
        assertTrue(saved.all { it.asset.uid == crypto.uid })
    }

    // ============================================================
    // TEST 1: VIEW USER PORTFOLIOS
    // ============================================================

    @Test
    fun `should return all portfolios from a user`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val (password2, salt2) = hashAndSaltPassword("Password2")

        val user1 = userRepository.save(
            User(
                name = "Name1",
                email = "Email1",
                password = password1,
                salt = salt1,
                role = "Viewer"
            )
        )

        val user2 = userRepository.save(
            User(
                name = "Name2",
                email = "Email2",
                password = password2,
                salt = salt2,
                role = "Viewer"
            )
        )

        val user1Id = user1.uid
        val user2Id = user2.uid

        // User 1 initially has one default portfolio.
        var response = authenticatedRequest(
            user1,
            HttpMethod.GET,
            "/user/$user1Id",
            List::class.java
        )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)

        // User 2 also has one default portfolio.
        response = authenticatedRequest(
            user2,
            HttpMethod.GET,
            "/user/$user2Id",
            List::class.java
        )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)

        // Add another portfolio for user 1.
        saveOnePortfolio(owner = user1)

        response = authenticatedRequest(
            user1,
            HttpMethod.GET,
            "/user/$user1Id",
            List::class.java
        )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)

        // User 2's portfolio count must remain unchanged.
        response = authenticatedRequest(
            user2,
            HttpMethod.GET,
            "/user/$user2Id",
            List::class.java
        )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)

        saveOnePortfolio(owner = user1)
    }

    // ============================================================
    // TEST 2: DELETE PORTFOLIO
    // ============================================================

    @Test
    fun `should not be able to delete main portfolio, but can delete others`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")

        val user1 = userRepository.save(
            User(
                name = "Name1",
                email = "Email1",
                password = password1,
                salt = salt1,
                role = "Trader"
            )
        )

        val user1Id = user1.uid

        val portfolio1 = saveOnePortfolio(
            owner = user1,
            main = true
        )
        val portfolio1Id = portfolio1.uid

        val portfolio2 = saveOnePortfolio(owner = user1)
        val portfolio2Id = portfolio2.uid

        saveOneAsset(portfolio = portfolio1)
        saveOneAsset(portfolio = portfolio2)

        var response = authenticatedRequest(
            user1,
            HttpMethod.GET,
            "/user/$user1Id",
            List::class.java
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(2, response.body?.size)

        // Main portfolio cannot be deleted.
        val deleteResponse1 = authenticatedRequest(
            user1,
            HttpMethod.DELETE,
            "/user/$user1Id/$portfolio1Id",
            String::class.java
        )

        assertEquals(
            "Cannot delete main portfolio",
            deleteResponse1.body
        )

        response = authenticatedRequest(
            user1,
            HttpMethod.GET,
            "/user/$user1Id",
            List::class.java
        )

        assertEquals(2, response.body?.size)

        // Non-main portfolio can be deleted.
        val deleteResponse2 = authenticatedRequest(
            user1,
            HttpMethod.DELETE,
            "/user/$user1Id/$portfolio2Id",
            String::class.java
        )

        assertEquals(
            "Deleted portfolio ${portfolio2.name}",
            deleteResponse2.body
        )

        response = authenticatedRequest(
            user1,
            HttpMethod.GET,
            "/user/$user1Id",
            List::class.java
        )

        assertEquals(1, response.body?.size)

        val portfolioHistory: List<PortfolioHistory> =
            portfolioHistoryRepository.findByPortfolio(portfolio1)

        assertEquals(2, portfolioHistory.size)
    }

    // ============================================================
    // TEST 3: UPDATE PORTFOLIO DESCRIPTION
    // ============================================================

    @Test
    fun `should be able to edit a portfolio - Description`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")

        val user = userRepository.save(
            User(
                name = "Name1",
                email = "Email1",
                password = password1,
                salt = salt1,
                role = "Trader"
            )
        )

        val userId = user.uid

        saveOnePortfolio(
            owner = user,
            main = true
        )

        val portfolio2 = saveOnePortfolio(owner = user)
        val portfolio2Id = portfolio2.uid

        val portfolioRequest = preparePortfolioRequest()

        val updatedResponse = authenticatedRequest(
            user,
            HttpMethod.PUT,
            "/$userId/$portfolio2Id",
            Portfolio::class.java,
            portfolioRequest
        )

        val updatedPortfolio =
            portfolioRepository.findOneByUid(portfolio2Id)

        assertEquals(200, updatedResponse.statusCode.value())
        assertEquals(portfolio2Id, updatedPortfolio.uid)
        assertEquals(portfolioRequest.name, updatedPortfolio.name)
        assertEquals(
            portfolioRequest.description,
            updatedPortfolio.description
        )
    }

    // ============================================================
    // TEST 4: TRANSFER CRYPTO ASSETS
    // ============================================================

    @Test
    fun `should be able to edit a portfolio - Assets`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")

        val user = userRepository.save(
            User(
                name = "Name1",
                email = "Email1",
                password = password1,
                salt = salt1,
                role = "Trader"
            )
        )

        val userId = user.uid

        val portfolio1 = saveOnePortfolio(
            owner = user,
            main = true
        )

        val portfolio2 = saveOnePortfolio(owner = user)
        val portfolio2Id = portfolio2.uid

        // Case 1: No asset available for transfer.
        val cryptoRequest = prepareCryptoRequest(
            portfolio_uid = portfolio2Id
        )

        val updatedResponse = authenticatedRequest(
            user,
            HttpMethod.POST,
            "/asset/crypto/update/$userId",
            String::class.java,
            cryptoRequest
        )

        assertEquals(404, updatedResponse.statusCode.value())
        assertNotNull(updatedResponse.body)

        // Case 2: Attempt to remove more than available.
        saveOneAsset(portfolio = portfolio1)

        val cryptoRequest2 = prepareCryptoRequest(
            portfolio_uid = portfolio2Id,
            action = "Remove"
        )

        val updatedResponse2 = authenticatedRequest(
            user,
            HttpMethod.POST,
            "/asset/crypto/update/$userId",
            String::class.java,
            cryptoRequest2
        )

        assertEquals(400, updatedResponse2.statusCode.value())
        assertEquals(
            "Insufficient asset quantity in source portfolio",
            updatedResponse2.body
        )

        // Case 3: Successfully transfer assets.
        val cryptoRequest3 = prepareCryptoRequest(
            portfolio_uid = portfolio2Id
        )

        val updatedResponse3 = authenticatedRequest(
            user,
            HttpMethod.POST,
            "/asset/crypto/update/$userId",
            String::class.java,
            cryptoRequest3
        )

        assertEquals(200, updatedResponse3.statusCode.value())
        assertEquals(
            "Transferred ${cryptoRequest3.quantity} from ${portfolio1.name} to ${portfolio2.name}.",
            updatedResponse3.body
        )

        val portfolioHistory1: List<PortfolioHistory> =
            portfolioHistoryRepository.findByPortfolio(portfolio1)

        assertEquals(2, portfolioHistory1.size)

        val portfolioHistory2: List<PortfolioHistory> =
            portfolioHistoryRepository.findByPortfolio(portfolio2)

        assertEquals(1, portfolioHistory2.size)
    }
}
