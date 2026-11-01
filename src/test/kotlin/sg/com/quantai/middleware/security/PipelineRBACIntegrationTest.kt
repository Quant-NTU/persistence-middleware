
package sg.com.quantai.middleware.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.http.ResponseEntity
import sg.com.quantai.middleware.configs.JwtService
import sg.com.quantai.middleware.data.mongo.Pipeline
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.repositories.mongo.PipelineRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PipelineRBACIntegrationTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val pipelineRepository: PipelineRepository,
    @Autowired private val restTemplate: TestRestTemplate,
    @Autowired private val jwtService: JwtService,
    @Autowired private val mongoTemplate: MongoTemplate,
    @LocalServerPort private val port: Int
) {

    private val expectedDatabase = "quantai_rbac_automated_test"

    private fun assertDatabaseIsolation() {
        check(mongoTemplate.db.name == expectedDatabase) {
            "Unsafe MongoDB database: ${mongoTemplate.db.name}"
        }
    }

    @BeforeEach
    fun setup() {
        // Fail immediately if Gradle is connected to the wrong database.
        assertDatabaseIsolation()
    }

    private fun createUser(role: String): User {
        assertDatabaseIsolation()

        return userRepository.save(
            User(
                name = "Pipeline RBAC Test User",
                email = "pipeline-rbac-${UUID.randomUUID()}@example.test",
                password = "not-used-for-login",
                salt = "not-used",
                isRegistered = true,
                role = role,
                isAdmin = role == "Admin"
            )
        )
    }

    private fun createPipeline(owner: User): Pipeline {
        assertDatabaseIsolation()

        return pipelineRepository.save(
            Pipeline(
                title = "RBAC Test Pipeline",
                owner = owner,
                description = "Created for pipeline authorization testing"
            )
        )
    }

    private fun request(
        method: HttpMethod,
        path: String,
        user: User? = null,
        body: Any? = null
    ): ResponseEntity<String> {
        val headers = HttpHeaders()

        if (user != null) {
            headers.setBearerAuth(jwtService.generateToken(user.uid))
        }

        return restTemplate.exchange(
            "http://localhost:$port$path",
            method,
            HttpEntity(body, headers),
            String::class.java
        )
    }

    private fun pipelineRequestBody(): Map<String, Any> =
        mapOf(
            "title" to "New RBAC Pipeline",
            "description" to "Pipeline created during RBAC testing",
            "strategies_id" to "",
            "portfolio_id" to ""
        )

    @Test
    fun `missing JWT cannot list pipelines`() {
        val response = request(
            HttpMethod.GET,
            "/pipelines"
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `viewer can list own pipelines`() {
        val viewer = createUser("Viewer")
        val ownPipeline = createPipeline(viewer)

        val response = request(
            HttpMethod.GET,
            "/pipelines/user/${viewer.uid}",
            viewer
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body.orEmpty().contains(ownPipeline.uid))
    }

    @Test
    fun `viewer cannot create pipeline`() {
        val viewer = createUser("Viewer")

        val response = request(
            HttpMethod.POST,
            "/pipelines/${viewer.uid}",
            viewer,
            pipelineRequestBody()
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `trader can create own pipeline`() {
        val trader = createUser("Trader")

        val response = request(
            HttpMethod.POST,
            "/pipelines/${trader.uid}",
            trader,
            pipelineRequestBody()
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertTrue(response.body.orEmpty().contains("New RBAC Pipeline"))
    }

    @Test
    fun `trader cannot list another user's pipelines`() {
        val trader = createUser("Trader")
        val otherTrader = createUser("Trader")
        createPipeline(otherTrader)

        val response = request(
            HttpMethod.GET,
            "/pipelines/user/${otherTrader.uid}",
            trader
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `trader cannot read another user's pipeline`() {
        val trader = createUser("Trader")
        val otherTrader = createUser("Trader")
        val otherPipeline = createPipeline(otherTrader)

        val response = request(
            HttpMethod.GET,
            "/pipelines/user/${otherTrader.uid}/${otherPipeline.uid}",
            trader
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `trader cannot create pipeline for another user`() {
        val trader = createUser("Trader")
        val otherTrader = createUser("Trader")

        val response = request(
            HttpMethod.POST,
            "/pipelines/${otherTrader.uid}",
            trader,
            pipelineRequestBody()
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `trader cannot update another user's pipeline`() {
        val trader = createUser("Trader")
        val otherTrader = createUser("Trader")
        val otherPipeline = createPipeline(otherTrader)

        val response = request(
            HttpMethod.PUT,
            "/pipelines/user/${otherTrader.uid}/${otherPipeline.uid}",
            trader,
            pipelineRequestBody()
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `trader cannot delete another user's pipeline`() {
        val trader = createUser("Trader")
        val otherTrader = createUser("Trader")
        val otherPipeline = createPipeline(otherTrader)

        val response = request(
            HttpMethod.DELETE,
            "/pipelines/user/${otherTrader.uid}/${otherPipeline.uid}",
            trader
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)

        assertDatabaseIsolation()
        assertTrue(pipelineRepository.findOneByUid(otherPipeline.uid) != null)
    }

    @Test
    fun `admin cannot list trading pipelines`() {
        val admin = createUser("Admin")

        val response = request(
            HttpMethod.GET,
            "/pipelines",
            admin
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `admin cannot create trading pipeline`() {
        val admin = createUser("Admin")

        val response = request(
            HttpMethod.POST,
            "/pipelines/${admin.uid}",
            admin,
            pipelineRequestBody()
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `admin cannot delete trader pipeline`() {
        val admin = createUser("Admin")
        val trader = createUser("Trader")
        val pipeline = createPipeline(trader)

        val response = request(
            HttpMethod.DELETE,
            "/pipelines/user/${trader.uid}/${pipeline.uid}",
            admin
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)

        assertDatabaseIsolation()
        assertTrue(pipelineRepository.findOneByUid(pipeline.uid) != null)
    }

    @Test
    fun `trader receives 404 for nonexistent own pipeline`() {
        val trader = createUser("Trader")

        val response = request(
            HttpMethod.GET,
            "/pipelines/user/${trader.uid}/${UUID.randomUUID()}",
            trader
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `pipeline listing does not expose another user's pipeline`() {
        val trader = createUser("Trader")
        val otherTrader = createUser("Trader")

        val ownPipeline = createPipeline(trader)
        val otherPipeline = createPipeline(otherTrader)

        val response = request(
            HttpMethod.GET,
            "/pipelines",
            trader
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body.orEmpty().contains(ownPipeline.uid))
        assertFalse(response.body.orEmpty().contains(otherPipeline.uid))
    }
}
