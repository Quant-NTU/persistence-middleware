
import sg.com.quantai.middleware.repositories.mongo.PortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.data.mongo.Portfolio
import sg.com.quantai.middleware.requests.PortfolioRequest

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
import java.time.LocalDateTime
import org.mindrot.jbcrypt.BCrypt
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod


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
        main: Boolean = false,
        owner: User = userRepository.save(User(name="Name", email="Email", password="Password", salt="Salt")),
    )=  
        portfolioRepository.save(
            Portfolio(   
                name = name,
                description = description,
                main = main,
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

       // 1 (Default) portfolio for user 1
       var response =
        restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)

        // 1 (Default) portfolio for user 2
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)

        saveOnePortfolio(owner = user1)
        // 2 portfolio for user 1
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

        saveOnePortfolio(owner = user1)
    }

    @Test
    fun `should delete an existing portfolio`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val (password2, salt2) = hashAndSaltPassword("Password2")
        val user1 = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val user2 = userRepository.save(User(name="Name2", email="Email2", password=password2, salt=salt2))
        val user1Id = user1.uid
        val user2Id = user2.uid

        val portfolio1_1_Id = saveOnePortfolio(owner = user1).uid
        val portfolio1_2_Id = saveOnePortfolio(owner = user1).uid
        val portfolio2_1_Id = saveOnePortfolio(owner = user2).uid

        var response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(3, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)
        assertEquals(2, response.body?.size)

        // Delete portfolio1_1 for user 1
        val deleteResponse1 = restTemplate.exchange(
            getRootUrl() + "/user/$user1Id/$portfolio1_1_Id",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )

        assertEquals("Deleted portfolio $portfolio1_1_Id", deleteResponse1.body)
    
        response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(2, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)
        assertEquals(2, response.body?.size)

        // Delete portfolio2_1 for user 2
        val deleteResponse2 = restTemplate.exchange(
            getRootUrl() + "/user/$user2Id/$portfolio2_1_Id",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )

        assertEquals("Deleted portfolio $portfolio2_1_Id", deleteResponse2.body)
    
        response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(2, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)
        assertEquals(1, response.body?.size)

        // Delete portfolio1_2 for user 1
        val deleteResponse3 = restTemplate.exchange(
            getRootUrl() + "/user/$user1Id/$portfolio1_2_Id",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )
        assertEquals("Deleted portfolio $portfolio1_2_Id", deleteResponse3.body)
    
        response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(1, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)
        assertEquals(1, response.body?.size)
    }

    @Test
    fun `should not delete main portfolio`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val user1 = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val user1Id = user1.uid

        val portfolio1_1_Id = saveOnePortfolio(owner = user1,main=true).uid
        val portfolio1_2_Id = saveOnePortfolio(owner = user1).uid

        var response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(2, response.body?.size)

        // Delete portfolio1_1 for user 1
        val deleteResponse1 = restTemplate.exchange(
            getRootUrl() + "/user/$user1Id/$portfolio1_1_Id",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )

        assertEquals("Cannot delete main portfolio.", deleteResponse1.body)
    
        response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(2, response.body?.size)
    
        // Delete portfolio1_2 for user 1
        val deleteResponse3 = restTemplate.exchange(
            getRootUrl() + "/user/$user1Id/$portfolio1_2_Id",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )
        assertEquals("Deleted portfolio $portfolio1_2_Id", deleteResponse3.body)
    
        response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(1, response.body?.size)
    }
}