package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.*
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.mindrot.jbcrypt.BCrypt

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortfolioControllerTest

@Autowired
constructor(
    private val portfolioRepository: PortfolioRepository,
    private val userRepository: UserRepository,
    private val restTemplate: TestRestTemplate
) {

    @LocalServerPort protected var port: Int = 0

    @BeforeEach
    fun setUp() {
        portfolioRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun getRootUrl(): String? = "http://localhost:$port/portfolio"

    private fun preparePortfolioRequest() =
            PortfolioRequest(symbol="STOCK1", name="Stock 1", quantity=BigDecimal(1), price=BigDecimal(1.1), platform="Platform")

    private fun preparePortfolioRequestDifferentPriceQuantity() =
        PortfolioRequest(symbol="STOCK1", name="Stock 1", quantity=BigDecimal(3), price=BigDecimal(3.3), platform="Platform")

    private fun preparePortfolioRequestDifferentPlatform() =
        PortfolioRequest(symbol="STOCK1", name="Stock 1", quantity=BigDecimal(1), price=BigDecimal(1.1), platform="Platform 2")

    private fun preparePortfolioRequestDifferentStock() =
        PortfolioRequest(symbol="STOCK2", name="Stock 2", quantity=BigDecimal(1), price=BigDecimal(1.1), platform="Platform")

    private fun hashAndSaltPassword(plainTextPassword: String, salt: String? = null): Pair<String, String> {

        val generatedSalt = salt ?: BCrypt.gensalt()
        val hashedPassword = BCrypt.hashpw(plainTextPassword, generatedSalt)

        return Pair(hashedPassword, generatedSalt)
    }

    private fun saveOnePortfolio(
        owner: User = userRepository.save(User(name="Name", email="Email", password="Password", salt="Salt"))
    ) =
        portfolioRepository.save(
            Portfolio(
                symbol="STOCK1",
                name="Stock 1",
                quantity=BigDecimal(1),
                price=BigDecimal(1.1),
                platform="Platform",
                owner = owner
            )
        )

    private fun saveOnePortfolioDifferentPlatform(
        owner: User = userRepository.save(User(name="Name", email="Email", password="Password", salt="Salt"))
    ) =
        portfolioRepository.save(
            Portfolio(
                symbol="STOCK1",
                name="Stock 1",
                quantity=BigDecimal(2),
                price=BigDecimal(2.2),
                platform="Platform 2",
                owner = owner
            )
        )

    private fun saveAnotherPortfolio(
        owner: User = userRepository.save(User(name="Name", email="Email", password="Password", salt="Salt"))
    ) =
        portfolioRepository.save(
            Portfolio(
                symbol="STOCK2",
                name="Stock 2",
                quantity=BigDecimal(2),
                price=BigDecimal(2.2),
                platform="Platform 2",
                owner = owner
            )
        )

    @Test
    fun `should return all portfolios from a user`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val (password2, salt2) = hashAndSaltPassword("Password2")
        val user1 = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val user2 = userRepository.save(User(name="Name2", email="Email2", password=password2, salt=salt2))
        val user1Id = user1.uid
        val user2Id = user2.uid

        // No portfolios for user 1
        var response =
                restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        // No portfolios for user 2
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOnePortfolio(owner = user1)
        // 1 portfolio for user 1
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)

        // No transactions for user 2
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOnePortfolio(owner = user1)
        // 2 transactions for user 1
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)

        // No transactions for user 2
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOnePortfolio(owner = user2)
        // 2 transactions for user 1
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)

        // 1 transaction for user 2
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)
    }

    @Test
    fun `should return all portfolio from a user by symbol`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val user = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val userId = user.uid

        //1 asset added for the user
        saveOnePortfolio(owner = user)
        var response =
            restTemplate.getForEntity(getRootUrl() + "/user/$userId/STOCK1", Array<Portfolio>::class.java)
        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("STOCK1", response.body?.get(0)?.symbol)
        assertEquals(1, response.body?.size)

        //Another asset with a different symbol
        saveAnotherPortfolio(owner = user)
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$userId/STOCK1", Array<Portfolio>::class.java)
        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("STOCK1", response.body?.get(0)?.symbol)
        assertEquals(1, response.body?.size)

        //Another asset with the same symbol but different platform
        saveOnePortfolioDifferentPlatform(owner = user)
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$userId/STOCK1", Array<Portfolio>::class.java)
        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("STOCK1", response.body?.get(0)?.symbol)
        assertEquals("Platform", response.body?.get(0)?.platform)
        assertEquals("STOCK1", response.body?.get(1)?.symbol)
        assertEquals("Platform 2", response.body?.get(1)?.platform)
        assertEquals(2, response.body?.size)
    }

    @Test
    fun `should create a new Portfolio asset or update it`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val user = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val userId = user.uid

        var portfolioRequest = preparePortfolioRequest()

        var response =
            restTemplate.postForEntity(
                getRootUrl() + "/$userId",
                portfolioRequest,
                Portfolio::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("STOCK1", response.body?.symbol)
        assertEquals("Platform", response.body?.platform)
        assertEquals(BigDecimal(1), response.body?.quantity)
        assertEquals(BigDecimal(1.1), response.body?.price)

        portfolioRequest = preparePortfolioRequestDifferentPlatform()

        response =
            restTemplate.postForEntity(
                getRootUrl() + "/$userId",
                portfolioRequest,
                Portfolio::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("STOCK1", response.body?.symbol)
        assertEquals("Platform 2", response.body?.platform)
        assertEquals(BigDecimal(1), response.body?.quantity)
        assertEquals(BigDecimal(1.1), response.body?.price)

        portfolioRequest = preparePortfolioRequestDifferentStock()

        response =
            restTemplate.postForEntity(
                getRootUrl() + "/$userId",
                portfolioRequest,
                Portfolio::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("STOCK2", response.body?.symbol)
        assertEquals("Platform", response.body?.platform)
        assertEquals(BigDecimal(1), response.body?.quantity)
        assertEquals(BigDecimal(1.1), response.body?.price)

        portfolioRequest = preparePortfolioRequestDifferentPriceQuantity()

        response =
            restTemplate.postForEntity(
                getRootUrl() + "/$userId",
                portfolioRequest,
                Portfolio::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("STOCK1", response.body?.symbol)
        assertEquals("Platform", response.body?.platform)
        assertEquals(BigDecimal(4), response.body?.quantity)
        assertEquals(BigDecimal(2.75), response.body?.price)
    }
}
