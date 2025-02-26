
import sg.com.quantai.middleware.repositories.mongo.PortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.data.mongo.Portfolio

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.mindrot.jbcrypt.BCrypt


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension::class)
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

    private fun getRootUrl(): String? = "http://localhost:$port/portfolios"
    
    private fun saveOnePortfolio(
        name: String = "Test",
        description: String = "Test2",
        isMain: Boolean = true,
        owner: User = userRepository.save(User(name="Name", email="Email", password="Password", salt="Salt")),
    )=  
        portfolioRepository.save(
            Portfolio(
                name = name,
                description = description,
                main = isMain,
                owner = owner,
            )
        )

    private fun hashAndSaltPassword(plainTextPassword: String, salt: String? = null): Pair<String, String> {

        val generatedSalt = salt ?: BCrypt.gensalt()
        val hashedPassword = BCrypt.hashpw(plainTextPassword, generatedSalt)

        return Pair(hashedPassword, generatedSalt)
    }

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
        // No portfolios for user 2
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOnePortfolio(owner = user1)
        // 2 portfolios for user 1
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
        // No portfolios for user 2
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOnePortfolio(owner = user2)
        // 2 portfolios for user 1
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
        // 1 portfolio for user 2
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)
    }
}