package sg.com.quantai.middleware.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import sg.com.quantai.middleware.configs.JwtService
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.data.mongo.Portfolio
import sg.com.quantai.middleware.repositories.mongo.PortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import sg.com.quantai.middleware.repositories.mongo.AccessRequestRepository
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CasbinFilterIntegrationTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val accessRequestRepository: AccessRequestRepository,
    @Autowired private val portfolioRepository: PortfolioRepository,
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
        assertDatabaseIsolation()
    }

    private fun createUser(
        role: String = "Viewer",
        admin: Boolean = false
    ): User {
        assertDatabaseIsolation()

        return userRepository.save(
            User(
                name = "RBAC Test User",
                email = "rbac-${UUID.randomUUID()}@example.test",
                password = "not-used-for-login",
                salt = "not-used",
                isRegistered = true,
                role = role,
                isAdmin = admin
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

    @Test
    fun `protected endpoint rejects missing JWT`() {
        val response = request(HttpMethod.GET, "/audit-logs")
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `protected endpoint rejects invalid JWT`() {
        val headers = HttpHeaders()
        headers.setBearerAuth("invalid.jwt.token")

        val response = restTemplate.exchange(
            "http://localhost:$port/audit-logs",
            HttpMethod.GET,
            HttpEntity<Any>(null, headers),
            String::class.java
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `viewer cannot access admin endpoint`() {
        val viewer = createUser()

        val response = request(
            HttpMethod.GET,
            "/audit-logs",
            viewer
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `viewer cannot deposit funds`() {
        val viewer = createUser()

        val response = request(
            HttpMethod.POST,
            "/portfolios/cash/deposit/${viewer.uid}/test-portfolio",
            viewer,
            emptyMap<String, String>()
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `trader cannot access admin endpoint`() {
        val trader = createUser(role = "Trader")

        val response = request(
            HttpMethod.GET,
            "/audit-logs",
            trader
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `admin can access audit logs`() {
        val admin = createUser(role = "Admin", admin = true)

        val response = request(
            HttpMethod.GET,
            "/audit-logs",
            admin
        )

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `unmapped route is denied`() {
        val viewer = createUser()

        val response = request(
            HttpMethod.GET,
            "/rbac-unmapped-test",
            viewer
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `demoted admin loses permission with existing JWT`() {
        val admin = createUser(role = "Admin", admin = true)
        val token = jwtService.generateToken(admin.uid)

        val headers = HttpHeaders().apply {
            setBearerAuth(token)
        }

        val url = "http://localhost:$port/audit-logs"

        val before = restTemplate.exchange(
            url,
            HttpMethod.GET,
            HttpEntity<Any>(null, headers),
            String::class.java
        )

        assertEquals(HttpStatus.OK, before.statusCode)

        assertDatabaseIsolation()

        val query = Query.query(
            Criteria.where("uid").`is`(admin.uid)
        )

        val update = Update()
            .set("role", "Viewer")
            .set("isAdmin", false)

        val result = mongoTemplate.updateFirst(query, update, User::class.java)

        assertEquals(1L, result.matchedCount)
        assertEquals("Viewer", userRepository.findOneByUid(admin.uid).role)

        val after = restTemplate.exchange(
            url,
            HttpMethod.GET,
            HttpEntity<Any>(null, headers),
            String::class.java
        )

        assertEquals(HttpStatus.FORBIDDEN, after.statusCode)
    }

    private fun createPortfolio(owner: User): Portfolio {
        assertDatabaseIsolation()

        return portfolioRepository.save(
            Portfolio(
                name = "RBAC Test ${UUID.randomUUID()}",
                description = "Automated ownership test",
                owner = owner
            )
        )
    }

    @Test
    fun `viewer can access own portfolios`() {
        val viewer = createUser()

        val response = request(
            HttpMethod.GET,
            "/portfolios/user/${viewer.uid}",
            viewer
        )

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `viewer cannot access another user's portfolios`() {
        val viewerA = createUser()
        val viewerB = createUser()
        createPortfolio(viewerB)

        val response = request(
            HttpMethod.GET,
            "/portfolios/user/${viewerB.uid}",
            viewerA
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `viewer can access own portfolio history`() {
        val viewer = createUser()
        val portfolio = createPortfolio(viewer)

        val response = request(
            HttpMethod.GET,
            "/portfolios/history/${viewer.uid}/${portfolio.uid}",
            viewer
        )

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `viewer cannot access another user's portfolio history`() {
        val viewerA = createUser()
        val viewerB = createUser()
        val portfolioB = createPortfolio(viewerB)

        val response = request(
            HttpMethod.GET,
            "/portfolios/history/${viewerA.uid}/${portfolioB.uid}",
            viewerA
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }


    @Test
    fun `viewer can request trader access`() {
        val viewer = createUser()

        val response = request(
            HttpMethod.POST,
            "/roles/request",
            viewer
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)

        val pending = accessRequestRepository.findByUserIdAndStatus(
            viewer.uid, "Pending"
        )

        org.junit.jupiter.api.Assertions.assertNotNull(pending)
        assertEquals("Trader", pending!!.requestedRole)
    }

    @Test
    fun `viewer cannot submit duplicate pending request`() {
        val viewer = createUser()

        val first = request(HttpMethod.POST, "/roles/request", viewer)
        val second = request(HttpMethod.POST, "/roles/request", viewer)

        assertEquals(HttpStatus.CREATED, first.statusCode)
        assertEquals(HttpStatus.CONFLICT, second.statusCode)

        val requests = accessRequestRepository.findByUserId(viewer.uid)
        assertEquals(1, requests.size)
    }

    @Test
    fun `viewer cannot approve trader request`() {
        val viewer = createUser()
        val applicant = createUser()

        assertEquals(
            HttpStatus.CREATED,
            request(HttpMethod.POST, "/roles/request", applicant).statusCode
        )

        val response = request(
            HttpMethod.POST,
            "/roles/approve?userId=${applicant.uid}",
            viewer
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("Viewer", userRepository.findOneByUid(applicant.uid).role)
    }

    @Test
    fun `admin approval grants trader role`() {
        val viewer = createUser()
        val admin = createUser(role = "Admin", admin = true)

        val token = jwtService.generateToken(viewer.uid)

        val requested = request(
            HttpMethod.POST,
            "/roles/request",
            viewer
        )
        assertEquals(HttpStatus.CREATED, requested.statusCode)

        val approved = request(
            HttpMethod.POST,
            "/roles/approve?userId=${viewer.uid}",
            admin
        )
        assertEquals(HttpStatus.OK, approved.statusCode)

        val updatedUser = userRepository.findOneByUid(viewer.uid)
        assertEquals("Trader", updatedUser.role)

        val approvedRequest =
            accessRequestRepository.findByUserIdAndStatus(
                viewer.uid, "Approved"
            )

        org.junit.jupiter.api.Assertions.assertNotNull(approvedRequest)
        assertEquals(admin.uid, approvedRequest!!.resolvedBy)

        // Reuse the original JWT, issued before approval.
        val headers = HttpHeaders().apply {
            setBearerAuth(token)
        }

        val traderResponse = restTemplate.exchange(
            "http://localhost:$port/portfolios/cash/deposit/${viewer.uid}/test-portfolio",
            HttpMethod.POST,
            HttpEntity(emptyMap<String, String>(), headers),
            String::class.java
        )

        // This endpoint should now pass the RBAC check.
        // It may return 400 or 404 because the test portfolio does not exist.
        org.junit.jupiter.api.Assertions.assertNotEquals(
            HttpStatus.FORBIDDEN,
            traderResponse.statusCode
        )
        org.junit.jupiter.api.Assertions.assertNotEquals(
            HttpStatus.UNAUTHORIZED,
            traderResponse.statusCode
        )
    }

    @Test
    fun `admin can reject trader request`() {
        val viewer = createUser()
        val admin = createUser(role = "Admin", admin = true)

        assertEquals(
            HttpStatus.CREATED,
            request(HttpMethod.POST, "/roles/request", viewer).statusCode
        )

        val rejected = request(
            HttpMethod.POST,
            "/roles/reject?userId=${viewer.uid}",
            admin
        )

        assertEquals(HttpStatus.OK, rejected.statusCode)
        assertEquals("Viewer", userRepository.findOneByUid(viewer.uid).role)

        val rejectedRequest =
            accessRequestRepository.findByUserIdAndStatus(
                viewer.uid, "Rejected"
            )

        org.junit.jupiter.api.Assertions.assertNotNull(rejectedRequest)
        assertEquals(admin.uid, rejectedRequest!!.resolvedBy)
    }


    @Test
    fun `viewer cannot recover access requests`() {
        val viewer = createUser()

        val response = request(HttpMethod.POST, "/roles/recover", viewer)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `admin can recover access requests`() {
        val admin = createUser(role = "Admin", admin = true)

        val response = request(HttpMethod.POST, "/roles/recover", admin)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `recovery restores stale viewer request to pending`() {
        val viewer = createUser()
        val admin = createUser(role = "Admin", admin = true)

        assertDatabaseIsolation()

        val accessRequest = accessRequestRepository.save(
            sg.com.quantai.middleware.data.mongo.AccessRequest(
                userId = viewer.uid,
                status = "Processing",
                processingStartedAt = java.time.LocalDateTime.now().minusMinutes(10),
                resolvedBy = admin.uid
            )
        )

        val response = request(HttpMethod.POST, "/roles/recover", admin)

        assertEquals(HttpStatus.OK, response.statusCode)

        val recovered = accessRequestRepository.findById(accessRequest.id.toHexString()).orElseThrow()

        assertEquals("Pending", recovered.status)
        assertEquals(null, recovered.processingStartedAt)
        assertEquals(null, recovered.resolvedBy)
        assertEquals("Viewer", userRepository.findOneByUid(viewer.uid).role)
    }

    @Test
    fun `recovery flags stale trader request for manual review`() {
        val trader = createUser(role = "Trader")
        val admin = createUser(role = "Admin", admin = true)

        assertDatabaseIsolation()

        val accessRequest = accessRequestRepository.save(
            sg.com.quantai.middleware.data.mongo.AccessRequest(
                userId = trader.uid,
                status = "Processing",
                processingStartedAt = java.time.LocalDateTime.now().minusMinutes(10),
                resolvedBy = admin.uid
            )
        )

        val response = request(HttpMethod.POST, "/roles/recover", admin)

        assertEquals(HttpStatus.OK, response.statusCode)
        org.junit.jupiter.api.Assertions.assertTrue(
            response.body?.contains("MANUAL_REVIEW_REQUIRED") == true
        )

        val unchanged = accessRequestRepository.findById(accessRequest.id.toHexString()).orElseThrow()

        assertEquals("Processing", unchanged.status)
        assertEquals("Trader", userRepository.findOneByUid(trader.uid).role)
    }

    @Test
    fun `concurrent approval and rejection cannot both succeed`() {
        val viewer = createUser()
        val admin1 = createUser(role = "Admin", admin = true)
        val admin2 = createUser(role = "Admin", admin = true)

        assertEquals(
            HttpStatus.CREATED,
            request(HttpMethod.POST, "/roles/request", viewer).statusCode
        )

        val start = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)

        try {
            val approve = pool.submit<Int> {
                start.await()
                request(
                    HttpMethod.POST,
                    "/roles/approve?userId=${viewer.uid}",
                    admin1
                ).statusCode.value()
            }

            val reject = pool.submit<Int> {
                start.await()
                request(
                    HttpMethod.POST,
                    "/roles/reject?userId=${viewer.uid}",
                    admin2
                ).statusCode.value()
            }

            start.countDown()

            val statuses = listOf(
                approve.get(30, java.util.concurrent.TimeUnit.SECONDS),
                reject.get(30, java.util.concurrent.TimeUnit.SECONDS)
            )

            assertEquals(1, statuses.count { it == 200 })

            val approved = accessRequestRepository.findByUserIdAndStatus(
                viewer.uid, "Approved"
            )
            val rejected = accessRequestRepository.findByUserIdAndStatus(
                viewer.uid, "Rejected"
            )

            val finalRole = userRepository.findOneByUid(viewer.uid).role

            if (approved != null) {
                assertEquals("Trader", finalRole)
                assertEquals(null, rejected)
            } else {
                org.junit.jupiter.api.Assertions.assertNotNull(rejected)
                assertEquals("Viewer", finalRole)
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
