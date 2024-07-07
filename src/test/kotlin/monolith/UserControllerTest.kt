package monolith

import java.time.LocalDateTime
import monolith.data.User
import monolith.data.UserRepository
import monolith.request.*
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.mindrot.jbcrypt.BCrypt

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserControllerTest
@Autowired
constructor(
        private val userRepository: UserRepository,
        private val restTemplate: TestRestTemplate
) {

    private val defaultUserId = ObjectId.get()
    private val defaultToken = "aaa111bbb222ccc333"


    @LocalServerPort protected var port: Int = 0

    @BeforeEach
    fun setUp() {
        userRepository.deleteAll()
    }

    private fun getRootUrl(): String? = "http://localhost:$port/users"
    private fun saveOneUser(
        name: String ="Name",
        email: String ="Email",
        password: String ="Password",
        salt: String ?=null,
        passwordToken: String =defaultToken,
        passwordTokenExpiration: LocalDateTime =LocalDateTime.now().plusHours(1),
        isAdmin: Boolean =false,
        has2fa: Boolean =false,
        secret2FA: String ="",
        id: ObjectId = ObjectId.get()
    ): User {
        val (hashedPassword, salt) = hashAndSaltPassword(password, salt)
        return userRepository.save(User(
                                name=name,
                                email=email,
                                password=hashedPassword,
                                salt=salt,
                                passwordToken=passwordToken,
                                passwordTokenExpiration=passwordTokenExpiration,
                                isAdmin=isAdmin,
                                has2fa = has2fa,
                                secret2FA = secret2FA,
                                uid=id.toString()))
    }
    private fun prepareUserRequest() = UserRequest("UpdatedName", "UpdatedEmail", "UpdatedPassword")
    private fun hashAndSaltPassword(plainTextPassword: String, salt: String? = null): Pair<String, String> {

        val generatedSalt = salt ?: BCrypt.gensalt()
        val hashedPassword = BCrypt.hashpw(plainTextPassword, generatedSalt)
    
        return Pair(hashedPassword, generatedSalt)
    }

    @Test
    fun `should return all users`() {
        // No users
        var response = restTemplate.getForEntity(getRootUrl(), List::class.java)
        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        // 1 user
        saveOneUser("Name2", "Email2", "Password2")
        response = restTemplate.getForEntity(getRootUrl(), List::class.java)
        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)

        // 2 users
        saveOneUser("Name3", "Email3", "Password3")
        response = restTemplate.getForEntity(getRootUrl(), List::class.java)
        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
    }

    @Test
    fun `should return single user by id`() {
        saveOneUser(id=defaultUserId)

        val response = restTemplate.getForEntity(getRootUrl() + "/$defaultUserId", User::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(defaultUserId.toString(), response.body?.uid)
    }

    // delete User
    @Test
    fun `should delete existing user`() {
        saveOneUser()

        val delete =
                restTemplate.exchange(
                        getRootUrl() + "/$defaultUserId",
                        HttpMethod.DELETE,
                        HttpEntity(null, HttpHeaders()),
                        ResponseEntity::class.java
                )


        assertEquals(204, delete.statusCode.value())
        assertThrows(EmptyResultDataAccessException::class.java) {
            userRepository.findOneByUid(defaultUserId.toString())
        }

    }

    // update operation
    @Test
    fun `should update existing user`() {
        saveOneUser(id=defaultUserId)
        val userRequest = prepareUserRequest()


        val updateResponse =
                restTemplate.exchange(
                        getRootUrl() + "/$defaultUserId",
                        HttpMethod.PUT,
                        HttpEntity(userRequest, HttpHeaders()),
                        User::class.java
                )
        val updatedUser = userRepository.findOneByUid(defaultUserId.toString())

        assertEquals(200, updateResponse.statusCode.value())
        assertEquals(defaultUserId.toString(), updatedUser.uid)
        assertEquals(userRequest.email, updatedUser.email)
    }

    @Test
    fun `should create new user`() {
        val userRequest = prepareUserRequest()

        val response = restTemplate.postForEntity(getRootUrl(), userRequest, User::class.java)

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body)
        assertNotNull(response.body?.uid)
        assertEquals(userRequest.email, response.body?.email)
    }

    // TODO: How to test the login? Maybe we should change the function name to "CheckUser"
    // something like that
    @Test
    fun `should check with a login-password pair exists`() {
        // Following the UserController.login control flow, we should have 3 assertions here:
        saveOneUser()

        var loginRequest1 = LoginRequest("Email", "Password")
        val loginRequest2 = LoginRequest("Email", "Password2")
        val loginRequest3 = LoginRequest("NoEmail", "Password")

        val response1 =
                restTemplate.postForEntity(
                        getRootUrl() + "/signin",
                        loginRequest1,
                        User::class.java
                )
        // 1: Happy Path: When user.name and user.password match -> tests users and HTTP Status Code
        // 200
        assertEquals(200, response1.statusCode.value())
        assertNotNull(response1.body)
        assertNotNull(response1.body?.uid)
        assertEquals(loginRequest1.email, response1.body?.email)

        // 2: Exception Path 1: When user.name and user.password doesn't match -> HTTP Status Code
        // 401
        val response2 =
                restTemplate.postForEntity(
                        getRootUrl() + "/signin",
                        loginRequest2,
                        User::class.java
                )

        assertEquals(401, response2.statusCode.value())
        assertNull(response2.body)

        // 3: Exception Path 2: When user doesn't exist -> HTTP Status Code 401
        val response3 =
                restTemplate.postForEntity(
                        getRootUrl() + "/signin",
                        loginRequest3,
                        User::class.java
                )

        assertEquals(401, response3.statusCode.value())
        assertNull(response3.body)
    }

    // TODO: This test will be used in the functionality about reseting password
    @Test
    fun `should return password reset token`() {
        // Following the UserController.resetPassword control flow
        saveOneUser()
        //var savedId = saveOneUser(id=ObjectId.get()).uid
        // 1: Happy Path: User password token is valid, expiration date is valid
        // -> must validate that the password was updated
        var authenticationRequest = AuthenticationRequest("Email", defaultToken)
        var response =
                restTemplate.postForEntity(
                        getRootUrl() + "/authenticate-token",
                        authenticationRequest,
                        User::class.java
                )

        assertEquals(200, response.statusCode.value())
        assertEquals(defaultToken, response.body?.passwordToken)
        if (response.body?.passwordTokenExpiration != null) {
            assertTrue(LocalDateTime.now().isBefore(response.body?.passwordTokenExpiration))
        }

        userRepository.deleteAll()
        saveOneUser(passwordTokenExpiration = LocalDateTime.now().plusHours(-1))
        //savedId = saveOneUser(id=ObjectId.get(), passwordTokenExpiration = LocalDateTime.now().plusHours(-1)).uid
        // 2: Exception Path 1: User password token is valid, but the expiration date is invalid ->
        // must validate that the password wasn't changed and an error message was update
        authenticationRequest = AuthenticationRequest("Email", defaultToken)
        response =
                restTemplate.postForEntity(
                        getRootUrl() + "/authenticate-token",
                        authenticationRequest,
                        User::class.java
                )

        assertEquals(401, response.statusCode.value())
        assertNull(response.body)

        userRepository.deleteAll()
        saveOneUser()
        //savedId = saveOneUser(id = ObjectId.get()).uid
        // 3: Exception Path 2: User password token is invalid, but the expiration date is valid ->
        // must validate that the password wasn't changed and an error message was update
        authenticationRequest = AuthenticationRequest("Email", "111aaa")
        response =
                restTemplate.postForEntity(
                        getRootUrl() + "/authenticate-token",
                        authenticationRequest,
                        User::class.java
                )
        assertEquals(401, response.statusCode.value())
        assertNull(response.body)

        userRepository.deleteAll()
        saveOneUser(passwordTokenExpiration = LocalDateTime.now().plusHours(-1))
        //savedId = saveOneUser(id=ObjectId.get(), passwordTokenExpiration = LocalDateTime.now().plusHours(-1)).uid
        // 4: Exception Path 3: Neither password token nor expiration date are valid -> must
        // validate that the password wasn't changed and an error message was update
        authenticationRequest = AuthenticationRequest("Email", "111aaa")
        response =
                restTemplate.postForEntity(
                        getRootUrl() + "/authenticate-token",
                        authenticationRequest,
                        User::class.java
                )
        assertEquals(401, response.statusCode.value())
        assertNull(response.body)
    }

    // TODO: This test updates the user password
    @Test
    fun `should update user password`() {
        // Same as the test `should return password reset token` -> Maybe there should be some
        // assertions that are valid here and not there
        var savedId = saveOneUser().uid

        // 1: Happy Path: User password token is valid, expiration date is valid
        var passwordRequest = PasswordRequest("Password3")
        var response =
                restTemplate.exchange(
                        getRootUrl() + "/reset-password/$savedId",
                        HttpMethod.PUT,
                        HttpEntity(passwordRequest, HttpHeaders()),
                        User::class.java
                )


        assertEquals(200, response.statusCode.value())
        val(password, _) = hashAndSaltPassword("Password3", response.body?.salt)
        assertEquals(password, response.body?.password)

        userRepository.deleteAll()
        savedId = saveOneUser().uid
        //savedId = saveOneUser(id=ObjectId.get()).uid
        // 2: Exception Path 1: Password is invalid. Validate that the password wasn't changed
        passwordRequest = PasswordRequest("Password")
        response =
        restTemplate.exchange(
                getRootUrl() + "/reset-password/$savedId",
                HttpMethod.PUT,
                HttpEntity(passwordRequest, HttpHeaders()),
                User::class.java
        )

        assertEquals(304, response.statusCode.value())
        assertNull(response.body)
    }

    //tests if identical user passwords generate separate hashes
    @Test
    fun `same password different hash`() {
        userRepository.deleteAll()

        val user1Id = saveOneUser("Name123", "Email123", "Password123").uid
        val user2Id = saveOneUser("Name223", "Email223", "Password123").uid

        val response1 = restTemplate.getForEntity(getRootUrl() + "/$user1Id", User::class.java)
        val response2 = restTemplate.getForEntity(getRootUrl() + "/$user2Id", User::class.java)

        assertEquals(200, response1.statusCode.value())
        assertEquals(200, response2.statusCode.value())
        assertNotNull(response1.body)
        assertNotNull(response2.body)
        assertNotEquals(response1.body?.password, response2.body?.password)
    }

    @Test
    fun `google oauth login for new user`() {
        userRepository.deleteAll()

        val newOAuthRequest = NameEmailRequest("NameOAuth", "EmailOAuth")

        val responseNewOAuthUser =
                restTemplate.postForEntity(
                        getRootUrl() + "/login/oauth2/google",
                        newOAuthRequest,
                        User::class.java
                )

        assertEquals(200, responseNewOAuthUser.statusCode.value())
        assertNotNull(responseNewOAuthUser.body)
        assertEquals(newOAuthRequest.email, responseNewOAuthUser.body?.email)
    }

    @Test
    fun `register for existing google user`() {
        userRepository.deleteAll()

        val newOAuthRequest = NameEmailRequest("NameOAuth", "EmailOAuth")

        val responseNewOAuthUser =
                restTemplate.postForEntity(
                        getRootUrl() + "/login/oauth2/google",
                        newOAuthRequest,
                        User::class.java
                )

        val newRegisterRequest = UserRequest("NameOAuth", "EmailOAuth", "Password")

        val responseRegisterExistingGoogleUser =
                restTemplate.postForEntity(
                        getRootUrl(),
                        newRegisterRequest,
                        User::class.java
                )
        
        assertEquals(202, responseRegisterExistingGoogleUser.statusCode.value())

        val oAuthConfirmationRequest = GoogleConfirmationRequest("EmailOAuth", "EmailOAuth", "NameOAuth", "PasswordOAuth", "SaltOAuth")
        val responseGoogleConfirmation =
                restTemplate.postForEntity(
                        getRootUrl() + "/google-confirmation",
                        oAuthConfirmationRequest,
                        User::class.java
                )

        assertEquals(200, responseGoogleConfirmation.statusCode.value())
        assertNotNull(responseGoogleConfirmation.body)
        assertNotNull(responseGoogleConfirmation.body?.password)
//        assertTrue(responseGoogleConfirmation.body.isRegistered)
    }

    @Test
    fun `google oauth login for existing registered user`() {
        userRepository.deleteAll()

        val existingUserId = saveOneUser().uid

        val existingUserRequest = NameEmailRequest("Name", "Email")

        val responseOAuthExistingRegisteredUser =
                restTemplate.postForEntity(
                        getRootUrl() + "/login/oauth2/google",
                        existingUserRequest,
                        User::class.java
                )
        
        assertEquals(202, responseOAuthExistingRegisteredUser.statusCode.value())

        val loginConfirmationRequest = AccountConfirmationRequest("Email", "Password", "Email")
        val responseGoogleConfirmation =
                restTemplate.postForEntity(
                        getRootUrl() + "/account-confirmation",
                        loginConfirmationRequest,
                        User::class.java
                )

        assertEquals(200, responseGoogleConfirmation.statusCode.value())
        assertNotNull(responseGoogleConfirmation.body)
//        assertTrue(responseGoogleConfirmation.body.isGoogleLogin)
    }

    @Test
    fun `activate admin user`() {
        userRepository.deleteAll()
        var user1 = saveOneUser(isAdmin=true)

        val adminRequest1 = AdminRequest(user1.email)
        val response1 = restTemplate.postForEntity(
            getRootUrl() + "/create-admin",
            adminRequest1,
            User::class.java
        )

        assertEquals(417, response1.statusCode.value())

        var user2 = saveOneUser(email="Email2", isAdmin=false)

        val adminRequest2 = AdminRequest(user2.email)
        val response2 = restTemplate.postForEntity(
            getRootUrl() + "/create-admin",
            adminRequest2,
            User::class.java
        )

        assertEquals(200, response2.statusCode.value())

//        assertTrue(response2.body?.isAdmin)
    }

    @Test
    fun `remove admin user`() {

        var user1 = saveOneUser(isAdmin=false)

        val adminRequest1 = AdminRequest(user1.email)
        val response1 = restTemplate.postForEntity(
            getRootUrl() + "/remove-admin",
            adminRequest1,
            User::class.java
        )

        assertEquals(417, response1.statusCode.value())

        var user2 = saveOneUser(email="Email2", isAdmin=true)

        val adminRequest2 = AdminRequest(user2.email)
        val response2 = restTemplate.postForEntity(
            getRootUrl() + "/remove-admin",
            adminRequest2,
            User::class.java
        )

        assertEquals(200, response2.statusCode.value())

//        assertTrue(response2.body?.isAdmin)
    }

    @Test
    fun `enable 2fa`() {
        userRepository.deleteAll()

        val secret = "ABCDEFGHIJKLMNOP1234"
        val enable2FARequest = Enable2FARequest(secret)

        val user1id = saveOneUser(email="Email1", has2fa=true).uid

        val response1 = restTemplate.postForEntity(
            getRootUrl() + "/enable2FA/" + user1id,
            enable2FARequest,
            User::class.java
        )

        assertEquals(417, response1.statusCode.value())

        val user2id = saveOneUser(email="Email2", has2fa=false).uid
        val response2 = restTemplate.postForEntity(
            getRootUrl() + "/enable2FA/" + user2id,
            enable2FARequest,
            User::class.java
        )

        assertEquals(200, response2.statusCode.value())
    }

    @Test
    fun `disable 2fa`() {
        userRepository.deleteAll()

        val user1id = saveOneUser(email="Email1", has2fa=false).uid

        val response1 = restTemplate.exchange(
            getRootUrl() + "/disable2FA/$user1id",
            HttpMethod.PUT,
            null,
            User::class.java
        )

        assertEquals(417, response1.statusCode.value())

        val user2id = saveOneUser(email="Email2", has2fa=true).uid
        val response2 = restTemplate.exchange(
            getRootUrl() + "/disable2FA/$user2id",
            HttpMethod.PUT,
            null,
            User::class.java
        )

        assertEquals(200, response2.statusCode.value())
    }
}
