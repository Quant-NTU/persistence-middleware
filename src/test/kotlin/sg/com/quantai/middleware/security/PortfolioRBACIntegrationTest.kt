
package sg.com.quantai.middleware.security

import sg.com.quantai.middleware.configs.JwtService

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import sg.com.quantai.middleware.data.mongo.Portfolio
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.repositories.mongo.PortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("rbac-test")
class PortfolioRBACIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var portfolioRepository: PortfolioRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @BeforeEach
    fun verifyDatabaseIsolation() {
        assertEquals(
            "quantai_rbac_automated_test",
            mongoTemplate.db.name,
            "Refusing to run RBAC tests against a non-test database"
        )
    }

    private fun createUser(role: String): User {
        val id = UUID.randomUUID().toString()

        return userRepository.save(
            User(
                name = "RBAC $role $id",
                email = "portfolio-rbac-$id@example.com",
                password = "test-password",
                salt = "test-salt",
                isRegistered = true,
                role = role,
                isAdmin = role == "Admin"
            )
        )
    }

    private fun createPortfolio(
        owner: User,
        main: Boolean = false,
        balance: BigDecimal = BigDecimal.ZERO
    ): Portfolio {
        return portfolioRepository.save(
            Portfolio(
                name = "RBAC Portfolio ${UUID.randomUUID()}",
                description = "Portfolio RBAC integration test",
                owner = owner,
                main = main,
                cashBalance = balance
            )
        )
    }

    private fun headers(user: User): HttpHeaders {
        return HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(jwtService.generateToken(user.uid))
        }
    }

    private fun request(
        method: HttpMethod,
        path: String,
        user: User? = null,
        body: Any? = null
    ): ResponseEntity<String> {
        val requestHeaders = if (user != null) {
            headers(user)
        } else {
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
        }

        return restTemplate.exchange(
            "http://localhost:$port$path",
            method,
            HttpEntity(body, requestHeaders),
            String::class.java
        )
    }

    private fun portfolioBody(): Map<String, Any> {
        return mapOf(
            "name" to "New RBAC Portfolio",
            "description" to "Created during authorization testing"
        )
    }

    private fun cashBody(amount: String = "100"): Map<String, Any> {
        return mapOf("amount" to amount)
    }

    // ============================================================
    // AUTHENTICATION
    // ============================================================

    @Test
    fun `missing JWT cannot view portfolios`() {
        val user = createUser("Trader")

        val response = request(
            HttpMethod.GET,
            "/portfolios/user/${user.uid}"
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    // ============================================================
    // VIEWER
    // ============================================================

    @Test
    fun `viewer can view own portfolios`() {
        val viewer = createUser("Viewer")
        createPortfolio(viewer)

        val response = request(
            HttpMethod.GET,
            "/portfolios/user/${viewer.uid}",
            viewer
        )

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `viewer cannot view another user's portfolios`() {
        val viewer = createUser("Viewer")
        val other = createUser("Trader")

        val response = request(
            HttpMethod.GET,
            "/portfolios/user/${other.uid}",
            viewer
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `viewer cannot create portfolio`() {
        val viewer = createUser("Viewer")

        val response = request(
            HttpMethod.POST,
            "/portfolios/${viewer.uid}",
            viewer,
            portfolioBody()
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `viewer cannot update portfolio`() {
        val viewer = createUser("Viewer")
        val portfolio = createPortfolio(viewer)

        val response = request(
            HttpMethod.PUT,
            "/portfolios/${viewer.uid}/${portfolio.uid}",
            viewer,
            portfolioBody()
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `viewer cannot deposit cash`() {
        val viewer = createUser("Viewer")
        val portfolio = createPortfolio(viewer)

        val response = request(
            HttpMethod.POST,
            "/portfolios/cash/deposit/${viewer.uid}/${portfolio.uid}",
            viewer,
            cashBody()
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    // ============================================================
    // TRADER
    // ============================================================

    @Test
    fun `trader can view own portfolios`() {
        val trader = createUser("Trader")
        createPortfolio(trader)

        val response = request(
            HttpMethod.GET,
            "/portfolios/user/${trader.uid}",
            trader
        )

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `trader cannot view another trader's portfolios`() {
        val trader = createUser("Trader")
        val other = createUser("Trader")

        val response = request(
            HttpMethod.GET,
            "/portfolios/user/${other.uid}",
            trader
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `trader can create own portfolio`() {
        val trader = createUser("Trader")

        val response = request(
            HttpMethod.POST,
            "/portfolios/${trader.uid}",
            trader,
            portfolioBody()
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `trader cannot create portfolio for another user`() {
        val trader = createUser("Trader")
        val other = createUser("Trader")

        val response = request(
            HttpMethod.POST,
            "/portfolios/${other.uid}",
            trader,
            portfolioBody()
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `trader cannot update another user's portfolio`() {
        val trader = createUser("Trader")
        val other = createUser("Trader")
        val portfolio = createPortfolio(other)

        val response = request(
            HttpMethod.PUT,
            "/portfolios/${trader.uid}/${portfolio.uid}",
            trader,
            portfolioBody()
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `trader cannot delete another user's portfolio`() {
        val trader = createUser("Trader")
        val other = createUser("Trader")
        val portfolio = createPortfolio(other)

        val response = request(
            HttpMethod.DELETE,
            "/portfolios/user/${trader.uid}/${portfolio.uid}",
            trader
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNotNull(portfolioRepository.findOneByUid(portfolio.uid))
    }

    @Test
    fun `trader cannot deposit into another user's portfolio`() {
        val trader = createUser("Trader")
        val other = createUser("Trader")
        val portfolio = createPortfolio(other)

        val response = request(
            HttpMethod.POST,
            "/portfolios/cash/deposit/${trader.uid}/${portfolio.uid}",
            trader,
            cashBody()
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)

        val unchanged = portfolioRepository.findOneByUid(portfolio.uid)
        assertNotNull(unchanged)
        assertEquals(
            0,
            unchanged.cashBalance.compareTo(BigDecimal.ZERO)
        )
    }

    @Test
    fun `trader cannot withdraw from another user's portfolio`() {
        val trader = createUser("Trader")
        val other = createUser("Trader")
        val portfolio = createPortfolio(
            other,
            balance = BigDecimal("500")
        )

        val response = request(
            HttpMethod.POST,
            "/portfolios/cash/withdraw/${trader.uid}/${portfolio.uid}",
            trader,
            cashBody()
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)

        val unchanged = portfolioRepository.findOneByUid(portfolio.uid)
        assertNotNull(unchanged)
        assertEquals(
            0,
            unchanged.cashBalance.compareTo(BigDecimal("500"))
        )
    }

    // ============================================================
    // ADMIN
    // ============================================================

    @Test
    fun `admin cannot view trading portfolios`() {
        val admin = createUser("Admin")

        val response = request(
            HttpMethod.GET,
            "/portfolios/user/${admin.uid}",
            admin
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `admin cannot create trading portfolios`() {
        val admin = createUser("Admin")

        val response = request(
            HttpMethod.POST,
            "/portfolios/${admin.uid}",
            admin,
            portfolioBody()
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }
}
