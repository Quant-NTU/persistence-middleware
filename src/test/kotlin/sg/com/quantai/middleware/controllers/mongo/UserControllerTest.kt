package sg.com.quantai.middleware.controllers.mongo

import org.springframework.data.mongodb.core.MongoTemplate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import sg.com.quantai.middleware.configs.JwtService
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import sg.com.quantai.middleware.requests.user.LoginRequest
import sg.com.quantai.middleware.requests.user.NameEmailRequest
import sg.com.quantai.middleware.requests.user.PasswordRequest
import sg.com.quantai.middleware.requests.user.UserRequest
import java.time.LocalDateTime

/**
 * WARNING: These integration tests delete all records in the configured users collection.
 * Run ONLY against a disposable test database, never development/production data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerTest(
    @Autowired private val mongoTemplate: MongoTemplate,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val restTemplate: TestRestTemplate,
    @Autowired private val jwtService: JwtService,
    @LocalServerPort private val port: Int
) {
    private fun url(path: String = "") = "http://localhost:$port/users$path"

    @BeforeEach
    fun setup() {
        check(mongoTemplate.db.name == "quantai_rbac_automated_test") {
            "REFUSING destructive cleanup: unsafe MongoDB database ${mongoTemplate.db.name}"
        }

        // IMPORTANT: configure an isolated test MongoDB before running this test suite.
        userRepository.deleteAll()
    }

    private fun saveUser(
        email: String = "user@example.test",
        password: String = "Password123!",
        role: String = "Viewer",
        admin: Boolean = false,
        resetToken: String? = null,
        expiresAt: LocalDateTime = LocalDateTime.now().plusHours(1)
    ): User {
        val salt = BCrypt.gensalt()
        return userRepository.save(
            User(
                name = "Test User", email = email,
                password = BCrypt.hashpw(password, salt), salt = salt,
                isRegistered = true, role = role, isAdmin = admin,
                passwordToken = resetToken, passwordTokenExpiration = expiresAt
            )
        )
    }

    private fun headers(user: User): HttpHeaders = HttpHeaders().apply {
        setBearerAuth(jwtService.generateToken(user.uid))
    }

    private fun request(
        method: HttpMethod,
        path: String,
        user: User?,
        body: Any? = null
    ): ResponseEntity<String> = restTemplate.exchange(
        url(path), method,
        HttpEntity(body, if (user == null) HttpHeaders() else headers(user)),
        String::class.java
    )

    @Test
    fun `registration creates a Viewer without leaking password hash`() {
        val response = restTemplate.postForEntity(
            url(), UserRequest("New User", "new@example.test", "Password123!"), String::class.java
        )
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertTrue(response.body.orEmpty().contains("new@example.test"))
        assertFalse(response.body.orEmpty().contains("Password123!"))
        assertFalse(response.body.orEmpty().contains("\"salt\""))
        assertEquals("Viewer", userRepository.findOneByEmail("new@example.test").role)
    }

    @Test
    fun `login returns access token and nested user`() {
        saveUser()
        val response = restTemplate.postForEntity(
            url("/signin"), LoginRequest("user@example.test", "Password123!"), String::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body.orEmpty().contains("\"accessToken\""))
        assertTrue(response.body.orEmpty().contains("\"user\""))
        assertFalse(response.body.orEmpty().contains("\"salt\""))
    }

    @Test
    fun `login rejects wrong password`() {
        saveUser()
        val response = restTemplate.postForEntity(
            url("/signin"), LoginRequest("user@example.test", "wrong"), String::class.java
        )
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `profile requires JWT and enforces ownership`() {
        val alice = saveUser(email = "alice@example.test")
        val bob = saveUser(email = "bob@example.test")
        assertEquals(HttpStatus.UNAUTHORIZED, request(HttpMethod.GET, "/${alice.uid}", null).statusCode)
        assertEquals(HttpStatus.OK, request(HttpMethod.GET, "/${alice.uid}", alice).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.GET, "/${bob.uid}", alice).statusCode)
        assertEquals(
            HttpStatus.FORBIDDEN,
            request(HttpMethod.PUT, "/${bob.uid}", alice, NameEmailRequest("Changed", "bob@example.test")).statusCode
        )
        val updated = request(
            HttpMethod.PUT, "/${alice.uid}", alice,
            NameEmailRequest("Alice Updated", "alice@example.test")
        )
        assertEquals(HttpStatus.OK, updated.statusCode)
        assertEquals("Alice Updated", userRepository.findOneByUid(alice.uid).name)
        assertFalse(updated.body.orEmpty().contains("\"password\""))
    }

    @Test
    fun `demoted admin loses audit permission with existing JWT`() {
        val admin = saveUser(email = "admin@example.test", role = "Admin", admin = true)
        val target = saveUser(email = "target@example.test", role = "Admin", admin = true)
        // Token minted BEFORE demotion.
        val oldToken = jwtService.generateToken(target.uid)
        val demoteHeaders = headers(admin)
        val demotion = restTemplate.exchange(
            url("/admin/${target.email}"), HttpMethod.DELETE,
            HttpEntity(mapOf("requesterUid" to admin.uid), demoteHeaders), String::class.java
        )
        assertEquals(HttpStatus.OK, demotion.statusCode)
        val demoted = userRepository.findOneByUid(target.uid)
        assertFalse(demoted.isAdmin)
        assertEquals("Viewer", demoted.role)

        val oldHeaders = HttpHeaders().apply { setBearerAuth(oldToken) }
        val oldJwtResponse = restTemplate.exchange(
            "http://localhost:$port/audit-logs", HttpMethod.GET,
            HttpEntity<Any>(null, oldHeaders), String::class.java
        )
        assertEquals(HttpStatus.FORBIDDEN, oldJwtResponse.statusCode)
        val freshJwtResponse = restTemplate.exchange(
            "http://localhost:$port/audit-logs", HttpMethod.GET,
            HttpEntity<Any>(null, headers(demoted)), String::class.java
        )
        assertEquals(HttpStatus.FORBIDDEN, freshJwtResponse.statusCode)
    }

    @Test
    fun `reset password requires valid unexpired token and prevents reuse`() {
        val user = saveUser(resetToken = "valid-reset-token")
        val bad = request(
            HttpMethod.PUT, "/reset-password/${user.uid}", null,
            PasswordRequest("NewPassword123!", "wrong-token")
        )
        assertEquals(HttpStatus.UNAUTHORIZED, bad.statusCode)
        val good = request(
            HttpMethod.PUT, "/reset-password/${user.uid}", null,
            PasswordRequest("NewPassword123!", "valid-reset-token")
        )
        assertEquals(HttpStatus.NO_CONTENT, good.statusCode)
        val updated = userRepository.findOneByUid(user.uid)
        assertNull(updated.passwordToken)
        assertTrue(BCrypt.checkpw("NewPassword123!", updated.password))
        val replay = request(
            HttpMethod.PUT, "/reset-password/${user.uid}", null,
            PasswordRequest("AnotherPassword123!", "valid-reset-token")
        )
        assertEquals(HttpStatus.UNAUTHORIZED, replay.statusCode)
    }

    @Test
    fun `email password reset validates token and prevents reuse`() {
        val user = saveUser(resetToken = "email-reset-token")

        val invalid = request(
            HttpMethod.PUT, "/reset-password-by-email", null,
            mapOf(
                "email" to user.email,
                "newPassword" to "NewPassword123!",
                "resetPasswordToken" to "wrong-token"
            )
        )
        assertEquals(HttpStatus.UNAUTHORIZED, invalid.statusCode)

        val valid = request(
            HttpMethod.PUT, "/reset-password-by-email", null,
            mapOf(
                "email" to user.email,
                "newPassword" to "NewPassword123!",
                "resetPasswordToken" to "email-reset-token"
            )
        )
        assertEquals(HttpStatus.NO_CONTENT, valid.statusCode)

        val updated = userRepository.findOneByUid(user.uid)
        assertNull(updated.passwordToken)
        assertTrue(BCrypt.checkpw("NewPassword123!", updated.password))

        val replay = request(
            HttpMethod.PUT, "/reset-password-by-email", null,
            mapOf(
                "email" to user.email,
                "newPassword" to "AnotherPassword123!",
                "resetPasswordToken" to "email-reset-token"
            )
        )
        assertEquals(HttpStatus.UNAUTHORIZED, replay.statusCode)
    }

    @Test
    fun `email password reset rejects expired token`() {
        val user = saveUser(
            resetToken = "expired-email-token",
            expiresAt = LocalDateTime.now().minusMinutes(1)
        )

        val response = request(
            HttpMethod.PUT, "/reset-password-by-email", null,
            mapOf(
                "email" to user.email,
                "newPassword" to "NewPassword123!",
                "resetPasswordToken" to "expired-email-token"
            )
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `email password reset rejects existing password`() {
        val user = saveUser(resetToken = "same-password-token")

        val response = request(
            HttpMethod.PUT, "/reset-password-by-email", null,
            mapOf(
                "email" to user.email,
                "newPassword" to "Password123!",
                "resetPasswordToken" to "same-password-token"
            )
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `expired reset token is rejected`() {
        val user = saveUser(resetToken = "expired", expiresAt = LocalDateTime.now().minusMinutes(1))
        val response = request(
            HttpMethod.PUT, "/reset-password/${user.uid}", null,
            PasswordRequest("NewPassword123!", "expired")
        )
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
