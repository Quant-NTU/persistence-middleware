
package sg.com.quantai.middleware.controllers.mongo

import java.security.MessageDigest
import java.time.LocalDateTime
import org.mindrot.jbcrypt.BCrypt
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.configs.JwtService
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.mailsender.config.EmailServiceImpl
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import sg.com.quantai.middleware.requests.user.AccountConfirmationRequest
import sg.com.quantai.middleware.requests.user.AdminRequest
import sg.com.quantai.middleware.requests.user.AuthenticationRequest
import sg.com.quantai.middleware.requests.user.Enable2FARequest
import sg.com.quantai.middleware.requests.user.GoogleConfirmationRequest
import sg.com.quantai.middleware.requests.user.LoginRequest
import sg.com.quantai.middleware.requests.user.NameEmailRequest
import sg.com.quantai.middleware.requests.user.PasswordRequest
import sg.com.quantai.middleware.requests.user.UpdateSessionDurationRequest
import sg.com.quantai.middleware.requests.user.UserRequest
import sg.com.quantai.middleware.responses.auth.AuthUser
import sg.com.quantai.middleware.responses.auth.LoginSuccessResponse
import sg.com.quantai.middleware.responses.auth.TwoFactorRequiredResponse
import sg.com.quantai.middleware.responses.auth.Verify2FARequest
import sg.com.quantai.middleware.security.TotpService
import sg.com.quantai.middleware.security.TwoFactorChallengeService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt

@RestController
@RequestMapping("/users")
class UserController(
    private val usersRepository: UserRepository,
    private val emailService: EmailServiceImpl,
    private val jwtService: JwtService,
    private val totpService: TotpService,
    private val challengeService: TwoFactorChallengeService,
) {

    // Retrieve all users
    @GetMapping
    fun getAllUsers(): ResponseEntity<List<User>> {
        val users = usersRepository.findAll()
        return ResponseEntity.ok(users)
    }

    // Build an authenticated response containing a JWT and safe user details
    private fun authenticatedResponse(user: User): LoginSuccessResponse {
        val effectiveRole = if (user.isAdmin) "Admin" else user.role

        return LoginSuccessResponse(
            accessToken = jwtService.generateToken(user.uid),
            user = AuthUser(
                uid = user.uid,
                name = user.name,
                email = user.email,
                role = effectiveRole,
                isAdmin = user.isAdmin,
                has2fa = user.has2fa
            )
        )
    }

    // Get a single user by UID
    @GetMapping("/{uid}")
    fun getOneUser(@PathVariable("uid") uid: String): ResponseEntity<User> {
        val user = usersRepository.findOneByUid(uid)
        return ResponseEntity.ok(user)
    }

    // Login using email and password
    @PostMapping("/signin")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Any> {
        if (!userExist(request.email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = usersRepository.findOneByEmail(request.email)

        if (user.password == null || user.salt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val (hashedPassword, _) = hashAndSaltPassword(
            request.password,
            user.salt
        )

        if (user.password != hashedPassword) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        if (user.has2fa) {
            if (user.secret2FA.isNullOrBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                TwoFactorRequiredResponse(
                    challengeToken = challengeService.create(user.uid)
                )
            )
        }

        val updatedUser = usersRepository.save(
            user.copy(lastLogin = LocalDateTime.now())
        )

        return ResponseEntity.ok(authenticatedResponse(updatedUser))
    }

    // Verify 2FA on the backend before issuing a JWT
    @PostMapping("/verify-2fa")
    fun verify2FA(
        @RequestBody request: Verify2FARequest
    ): ResponseEntity<Any> {
        val uid = challengeService.verify(
            request.challengeToken,
            request.code
        ) { challengedUid, code ->

            val user = usersRepository.findOneByUid(challengedUid)
            val secret = user.secret2FA

            user.has2fa &&
                !secret.isNullOrBlank() &&
                totpService.verifyCode(secret, code)
        }

        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = usersRepository.findOneByUid(uid)

        if (!user.has2fa) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val updatedUser = usersRepository.save(
            user.copy(lastLogin = LocalDateTime.now())
        )

        return ResponseEntity.ok(authenticatedResponse(updatedUser))
    }

    // Google OAuth login
    @PostMapping("/login/oauth2/google")
    fun googleOAuthLogin(
        @RequestBody request: NameEmailRequest
    ): ResponseEntity<User> {
        if (userExist(request.email)) {
            val user = usersRepository.findOneByEmail(request.email)

            if (user.isGoogleLogin) {
                if (!user.has2fa) {
                    val googleLoginUser = usersRepository.save(
                        user.copy(lastLogin = LocalDateTime.now())
                    )
                    return ResponseEntity(googleLoginUser, HttpStatus.OK)
                } else {
                    return ResponseEntity(user, HttpStatus.ACCEPTED)
                }
            } else {
                return ResponseEntity(user, HttpStatus.ACCEPTED)
            }
        } else {
            val user = usersRepository.save(
                User(
                    name = request.name,
                    email = request.email,
                    role = "Viewer",
                    isGoogleLogin = true,
                    lastLogin = LocalDateTime.now()
                )
            )
            return ResponseEntity(user, HttpStatus.OK)
        }
    }

    // Retrieve admin or non-admin users
    @GetMapping("/list")
    fun getAdminOrNonAdminUsers(
        @RequestParam admins: Boolean,
        @RequestParam uid: String
    ): ResponseEntity<List<User>> {
        val user = usersRepository.findOneByUid(uid)

        if (!user.isAdmin) {
            return ResponseEntity(HttpStatus.UNAUTHORIZED)
        }

        val nonAdminUsers = usersRepository.findAllByIsAdmin(admins)
        return ResponseEntity(nonAdminUsers, HttpStatus.OK)
    }

    // Count admin and non-admin users
    @GetMapping("/count")
    fun countAdminAndNonAdminUsers(): ResponseEntity<Map<String, Long>> {
        val adminUserCount = usersRepository.countByIsAdmin(true)
        val nonAdminUserCount = usersRepository.countByIsAdmin(false)

        val countMap = mapOf(
            "adminUsers" to adminUserCount,
            "nonAdminUsers" to nonAdminUserCount
        )

        return ResponseEntity.ok(countMap)
    }

    // Promote user to Admin
    @PutMapping("/admin/{email}")
    fun promoteToAdminUser(
        @PathVariable email: String,
        @RequestBody request: AdminRequest
    ): ResponseEntity<User> {
        val requester = usersRepository.findOneByUid(request.requesterUid)
        val adminCount = usersRepository.countByIsAdmin(true)

        if (!requester.isAdmin && adminCount != 0L) {
            return ResponseEntity(HttpStatus.UNAUTHORIZED)
        }

        if (userExist(email)) {
            if (checkAdminStatus(email)) {
                return ResponseEntity(HttpStatus.EXPECTATION_FAILED)
            }

            val user = usersRepository.findOneByEmail(email)

            val newAdmin = usersRepository.save(
                user.copy(
                    isAdmin = true,
                    updatedDate = LocalDateTime.now()
                )
            )

            return ResponseEntity(newAdmin, HttpStatus.OK)
        } else {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Demote an Admin
    @DeleteMapping("/admin/{email}")
    fun demoteFromAdminUser(
        @PathVariable email: String,
        @RequestBody request: AdminRequest
    ): ResponseEntity<User> {
        val requester = usersRepository.findOneByUid(request.requesterUid)

        if (!requester.isAdmin) {
            return ResponseEntity(HttpStatus.UNAUTHORIZED)
        }

        if (userExist(email)) {
            if (!checkAdminStatus(email)) {
                return ResponseEntity(HttpStatus.EXPECTATION_FAILED)
            }

            val user = usersRepository.findOneByEmail(email)

            val newAdmin = usersRepository.save(
                user.copy(
                    isAdmin = false,
                    updatedDate = LocalDateTime.now()
                )
            )

            return ResponseEntity(newAdmin, HttpStatus.OK)
        } else {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Confirm Google registration
    @PostMapping("/google-confirmation")
    fun googleConfirmation(
        @RequestBody request: GoogleConfirmationRequest
    ): ResponseEntity<User> {
        if (request.oauthEmail == request.registrationEmail) {
            val user = usersRepository.findOneByEmail(request.oauthEmail)

            val updatedUser = usersRepository.save(
                user.copy(
                    name = request.name,
                    email = request.oauthEmail,
                    password = request.password,
                    salt = request.salt,
                    isRegistered = true,
                    updatedDate = LocalDateTime.now(),
                    lastLogin = LocalDateTime.now()
                )
            )

            if (!updatedUser.has2fa) {
                return ResponseEntity(updatedUser, HttpStatus.OK)
            } else {
                return ResponseEntity(updatedUser, HttpStatus.ACCEPTED)
            }
        } else {
            return ResponseEntity(HttpStatus.EXPECTATION_FAILED)
        }
    }

    // Confirm account registration
    @PostMapping("/account-confirmation")
    fun accountConfirmation(
        @RequestBody request: AccountConfirmationRequest
    ): ResponseEntity<User> {
        if (request.registrationEmail == request.oauthEmail) {
            val user = usersRepository.findOneByEmail(request.registrationEmail)

            val (hashedPassword, _) = hashAndSaltPassword(
                request.password,
                user.salt
            )

            if (user.password == hashedPassword) {
                val updatedUser = usersRepository.save(
                    user.copy(
                        isGoogleLogin = true,
                        updatedDate = LocalDateTime.now(),
                        lastLogin = LocalDateTime.now()
                    )
                )

                if (!updatedUser.has2fa) {
                    return ResponseEntity(updatedUser, HttpStatus.OK)
                } else {
                    return ResponseEntity(updatedUser, HttpStatus.ACCEPTED)
                }
            } else {
                return ResponseEntity(HttpStatus.UNAUTHORIZED)
            }
        } else {
            return ResponseEntity(HttpStatus.EXPECTATION_FAILED)
        }
    }

    // Enable 2FA
    
    // Enable 2FA only after verifying the authenticator code
    @PostMapping("/enable2FA/{uid}")
    fun enable2FA(
        @RequestBody request: Enable2FARequest,
        @PathVariable("uid") uid: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val authenticatedUid = jwt.subject

        if (authenticatedUid != uid) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("You can only enable 2FA for your own account")
        }

        val user = usersRepository.findOneByUid(authenticatedUid)

        if (user.has2fa) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("2FA is already enabled")
        }

        val secret = request.secret2FA

        if (secret.isBlank()) {
            return ResponseEntity.badRequest()
                .body("A 2FA secret is required")
        }

        val code = request.code

        if (!totpService.verifyCode(secret, code)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Invalid 2FA verification code")
        }

        val updatedUser = usersRepository.save(
            user.copy(
                has2fa = true,
                secret2FA = secret,
                updatedDate = LocalDateTime.now()
            )
        )

        return ResponseEntity.ok(
            mapOf(
                "uid" to updatedUser.uid,
                "has2fa" to updatedUser.has2fa,
                "message" to "2FA enabled successfully"
            )
        )
    }

    // Disable 2FA for the authenticated account
    @PutMapping("/disable2FA/{uid}")
    fun disable2FA(
        @PathVariable("uid") uid: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val authenticatedUid = jwt.subject

        if (authenticatedUid != uid) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("You can only disable 2FA for your own account")
        }

        val user = usersRepository.findOneByUid(authenticatedUid)

        if (!user.has2fa) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("2FA is not enabled")
        }

        val updatedUser = usersRepository.save(
            user.copy(
                has2fa = false,
                secret2FA = "",
                updatedDate = LocalDateTime.now()
            )
        )

        return ResponseEntity.ok(
            mapOf(
                "uid" to updatedUser.uid,
                "has2fa" to updatedUser.has2fa,
                "message" to "2FA disabled successfully"
            )
        )
    }

    // Register a user
    @PostMapping
    fun createUser(@RequestBody request: UserRequest): ResponseEntity<User> {
        if (userExist(request.email)) {
            val existingUser = usersRepository.findOneByEmail(request.email)

            if (existingUser.isRegistered) {
                return ResponseEntity(HttpStatus.NO_CONTENT)
            } else {
                val (hashedPassword, salt) = hashAndSaltPassword(request.password)

                val updatedExistingUser = existingUser.copy(
                    name = request.name,
                    email = request.email,
                    password = hashedPassword,
                    salt = salt,
                    updatedDate = LocalDateTime.now(),
                    isRegistered = true,
                    isAdmin = false
                )

                return ResponseEntity(updatedExistingUser, HttpStatus.ACCEPTED)
            }
        } else {
            val (hashedPassword, salt) = hashAndSaltPassword(request.password)

            val user = usersRepository.save(
                User(
                    name = request.name,
                    email = request.email,
                    role = "Viewer",
                    password = hashedPassword,
                    salt = salt,
                    isRegistered = true
                )
            )

            return ResponseEntity(user, HttpStatus.CREATED)
        }
    }

    // Update user profile
    @PutMapping("/{uid}")
    fun updateUser(
        @RequestBody request: NameEmailRequest,
        @PathVariable("uid") uid: String
    ): ResponseEntity<User> {
        val user = usersRepository.findOneByUid(uid)

        if (userExist(request.email) && request.email != user.email) {
            return ResponseEntity(HttpStatus.FORBIDDEN)
        } else {
            val updatedUser = usersRepository.save(
                user.copy(
                    name = request.name,
                    email = request.email,
                    updatedDate = LocalDateTime.now()
                )
            )

            return ResponseEntity.ok(updatedUser)
        }
    }

    // Reset password
    @PutMapping("/reset-password/{uid}")
    fun updatePassword(
        @RequestBody request: PasswordRequest,
        @PathVariable("uid") uid: String
    ): ResponseEntity<User> {
        val user = usersRepository.findOneByUid(uid)

        val (hashedPassword, salt) = hashAndSaltPassword(
            request.newPassword,
            user.salt
        )

        if (user.password != hashedPassword) {
            val updatedUser = usersRepository.save(
                user.copy(
                    password = hashedPassword,
                    salt = salt,
                    passwordToken = null,
                    passwordTokenExpiration = LocalDateTime.now(),
                    updatedDate = LocalDateTime.now()
                )
            )

            return ResponseEntity.ok(updatedUser)
        } else {
            return ResponseEntity(HttpStatus.NOT_MODIFIED)
        }
    }

    // Hash and salt a password
    fun hashAndSaltPassword(
        plainTextPassword: String,
        salt: String? = null
    ): Pair<String, String> {
        val generatedSalt = salt ?: BCrypt.gensalt()
        val hashedPassword = BCrypt.hashpw(plainTextPassword, generatedSalt)

        return Pair(hashedPassword, generatedSalt)
    }

    // Authenticate password-reset token
    @PostMapping("/authenticate-token")
    fun authenticateToken(
        @RequestBody request: AuthenticationRequest
    ): ResponseEntity<User> {
        val user = usersRepository.findOneByEmail(request.email)

        if (validToken(user.passwordToken, request.resetPasswordToken)) {
            if (validTokenExpiration(user.passwordTokenExpiration)) {
                return ResponseEntity.ok(user)
            } else {
                return ResponseEntity(HttpStatus.UNAUTHORIZED)
            }
        } else {
            return ResponseEntity(HttpStatus.UNAUTHORIZED)
        }
    }

    // Generate password-reset token
    @PostMapping("/generate-token")
    fun generatePasswordToken(
        @RequestBody request: NameEmailRequest
    ): ResponseEntity<User> {
        if (userExist(request.email)) {
            val user = usersRepository.findOneByEmail(request.email)

            val updatedUser = usersRepository.save(
                user.copy(
                    passwordToken = getRandomString(30),
                    passwordTokenExpiration = LocalDateTime.now().plusHours(1),
                    updatedDate = LocalDateTime.now()
                )
            )

            emailService.sendResetPasswordEmail(
                updatedUser.name,
                updatedUser.email,
                updatedUser.passwordToken
            )

            return ResponseEntity.ok(updatedUser)
        } else {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Update session duration
    @PostMapping("/update-session-duration")
    fun updateSessionDuration(
        @RequestBody request: UpdateSessionDurationRequest
    ): ResponseEntity<User> {
        if (userExist(request.email)) {
            val user = usersRepository.findOneByEmail(request.email)

            val updatedUser = usersRepository.save(
                user.copy(
                    sessionDuration = request.sessionDuration,
                    updatedDate = LocalDateTime.now()
                )
            )

            return ResponseEntity(updatedUser, HttpStatus.OK)
        } else {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Toggle session timeout
    @PostMapping("/update-session-timeout-status")
    fun updateSessionTimeoutStatus(
        @RequestBody request: NameEmailRequest
    ): ResponseEntity<User> {
        if (userExist(request.email)) {
            val user = usersRepository.findOneByEmail(request.email)

            val updatedUser = usersRepository.save(
                user.copy(
                    hasSessionTimeout = !user.hasSessionTimeout,
                    updatedDate = LocalDateTime.now()
                )
            )

            return ResponseEntity(updatedUser, HttpStatus.OK)
        } else {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    // Count users by session timeout status
    @GetMapping("/count-session-timeout")
    fun countUsersBySessionTimeout(): ResponseEntity<Map<String, Long>> {
        val timeoutEnabledUserCount = usersRepository.countByHasSessionTimeout(true)
        val nonTimeoutEnabledUserCount = usersRepository.countByHasSessionTimeout(false)

        val countMap = mapOf(
            "timeoutEnabledUsers" to timeoutEnabledUserCount,
            "nonTimeoutEnabledUsers" to nonTimeoutEnabledUserCount
        )

        return ResponseEntity.ok(countMap)
    }

    // Generate a random string
    fun getRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length).map { allowedChars.random() }.joinToString("")
    }

    // Compare password-reset tokens
    fun validToken(userToken: String?, token: String): Boolean {
        if (userToken.isNullOrBlank() || token.isBlank()) {
            return false
        }

        return MessageDigest.isEqual(
            userToken.toByteArray(Charsets.UTF_8),
            token.toByteArray(Charsets.UTF_8)
        )
    }

    // Check password-reset token expiration
    fun validTokenExpiration(tokenExpiration: LocalDateTime): Boolean {
        return tokenExpiration > LocalDateTime.now()
    }

    // Delete user
    @DeleteMapping("/{uid}")
    fun deleteUser(@PathVariable("uid") uid: String): ResponseEntity<Unit> {
        usersRepository.deleteById(uid)
        return ResponseEntity.noContent().build()
    }

    // Retrieve all users
    fun getUsers(): List<User> {
        return usersRepository.findAll()
    }

    // Check whether a user exists
    fun userExist(email: String): Boolean {
        val listOfUsers: List<User> = getUsers()

        for (user in listOfUsers) {
            if (user.email == email) {
                return true
            }
        }

        return false
    }

    // Check whether a user is an Admin
    fun checkAdminStatus(email: String): Boolean {
        val adminUsers: List<User> = usersRepository.findAllByIsAdmin(true)

        for (adminUser in adminUsers) {
            if (email == adminUser.email) {
                return true
            }
        }

        return false
    }
}