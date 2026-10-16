package sg.com.quantai.middleware.controllers.mongo

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64
import org.mindrot.jbcrypt.BCrypt
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import sg.com.quantai.middleware.configs.JwtService
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.mailsender.config.EmailServiceImpl
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import sg.com.quantai.middleware.requests.user.*
import sg.com.quantai.middleware.responses.auth.AuthUser
import sg.com.quantai.middleware.responses.auth.LoginSuccessResponse
import sg.com.quantai.middleware.responses.auth.TwoFactorRequiredResponse
import sg.com.quantai.middleware.responses.auth.Verify2FARequest
import sg.com.quantai.middleware.security.TotpService
import sg.com.quantai.middleware.security.TwoFactorChallengeService

@RestController
@RequestMapping("/users")
class UserController(
    private val usersRepository: UserRepository,
    private val emailService: EmailServiceImpl,
    private val jwtService: JwtService,
    private val totpService: TotpService,
    private val challengeService: TwoFactorChallengeService,
) {
    private val secureRandom = SecureRandom()


    private fun safeUser(user: User): AuthUser = AuthUser(
        uid = user.uid,
        name = user.name,
        email = user.email,
        role = if (user.isAdmin) "Admin" else user.role,
        isAdmin = user.isAdmin,
        has2fa = user.has2fa,
        hasSessionTimeout = user.hasSessionTimeout,
        sessionDuration = user.sessionDuration,
        lastLogin = user.lastLogin
    )


    private fun authenticatedResponse(user: User) = LoginSuccessResponse(
        accessToken = jwtService.generateToken(user.uid),
        user = safeUser(user)
    )

    private fun isOwnUser(uid: String, jwt: Jwt): Boolean = uid == jwt.subject

    @GetMapping
    fun getAllUsers(): ResponseEntity<List<AuthUser>> =
        ResponseEntity.ok(usersRepository.findAll().map(::safeUser))

    @GetMapping("/{uid}")
    fun getOneUser(
        @PathVariable uid: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        if (!isOwnUser(uid, jwt)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val user = try { usersRepository.findOneByUid(uid) } catch (_: Exception) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(safeUser(user))
    }

    @PostMapping("/signin")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Any> {
        val user = try { usersRepository.findOneByEmail(request.email) } catch (_: Exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        if (user.password.isNullOrBlank() || user.salt.isNullOrBlank() ||
            !BCrypt.checkpw(request.password, user.password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        if (user.has2fa) {
            if (user.secret2FA.isNullOrBlank()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                TwoFactorRequiredResponse(challengeToken = challengeService.create(user.uid))
            )
        }
        return ResponseEntity.ok(authenticatedResponse(usersRepository.save(user.copy(lastLogin = LocalDateTime.now()))))
    }

    @PostMapping("/verify-2fa")
    fun verify2FA(@RequestBody request: Verify2FARequest): ResponseEntity<Any> {
        val uid = challengeService.verify(request.challengeToken, request.code) { challengedUid, code ->
            val user = try { usersRepository.findOneByUid(challengedUid) } catch (_: Exception) { null }
            user != null && user.has2fa && !user.secret2FA.isNullOrBlank() &&
                totpService.verifyCode(user.secret2FA, code)
        } ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val user = try { usersRepository.findOneByUid(uid) } catch (_: Exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        if (!user.has2fa) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(authenticatedResponse(usersRepository.save(user.copy(lastLogin = LocalDateTime.now()))))
    }

    // This legacy OAuth endpoint does not verify a Google identity token.
    // It must not authenticate or create accounts based solely on a supplied email.
    @PostMapping("/login/oauth2/google")
    fun googleOAuthLogin(@RequestBody request: NameEmailRequest): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Verified Google OAuth integration required")

    @GetMapping("/list")
    fun getAdminOrNonAdminUsers(
        @RequestParam admins: Boolean,
        @RequestParam(required = false) uid: String?,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val requester = try { usersRepository.findOneByUid(jwt.subject) } catch (_: Exception) { null }
        if (requester?.isAdmin != true || requester.role != "Admin") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return ResponseEntity.ok(usersRepository.findAllByIsAdmin(admins).map(::safeUser))
    }

    @GetMapping("/count")
    fun countAdminAndNonAdminUsers(): ResponseEntity<Map<String, Long>> = ResponseEntity.ok(
        mapOf(
            "adminUsers" to usersRepository.countByIsAdmin(true),
            "nonAdminUsers" to usersRepository.countByIsAdmin(false)
        )
    )

    @PutMapping("/admin/{email}")
    fun promoteToAdminUser(
        @PathVariable email: String,
        @RequestBody request: AdminRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val requester = try { usersRepository.findOneByUid(jwt.subject) } catch (_: Exception) { null }
        if (requester?.isAdmin != true || requester.role != "Admin") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val user = try { usersRepository.findOneByEmail(email) } catch (_: Exception) { null }
            ?: return ResponseEntity.notFound().build()
        if (user.isAdmin) return ResponseEntity.status(HttpStatus.CONFLICT).build()
        val promoted = usersRepository.save(user.copy(isAdmin = true, role = "Admin", updatedDate = LocalDateTime.now()))
        return ResponseEntity.ok(safeUser(promoted))
    }

    @DeleteMapping("/admin/{email}")
    fun demoteFromAdminUser(
        @PathVariable email: String,
        @RequestBody request: AdminRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val requester = try { usersRepository.findOneByUid(jwt.subject) } catch (_: Exception) { null }
        if (requester?.isAdmin != true || requester.role != "Admin") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val user = try { usersRepository.findOneByEmail(email) } catch (_: Exception) { null }
            ?: return ResponseEntity.notFound().build()
        if (!user.isAdmin) return ResponseEntity.status(HttpStatus.CONFLICT).build()
        if (user.uid == jwt.subject) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val demoted = usersRepository.save(user.copy(isAdmin = false, role = "Viewer", updatedDate = LocalDateTime.now()))
        return ResponseEntity.ok(safeUser(demoted))
    }

    @PostMapping("/google-confirmation")
    fun googleConfirmation(@RequestBody request: GoogleConfirmationRequest): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Verified Google OAuth integration required")

    @PostMapping("/account-confirmation")
    fun accountConfirmation(@RequestBody request: AccountConfirmationRequest): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Verified Google OAuth integration required")

    @PostMapping("/enable2FA/{uid}")
    fun enable2FA(
        @RequestBody request: Enable2FARequest,
        @PathVariable uid: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        if (!isOwnUser(uid, jwt)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val user = try { usersRepository.findOneByUid(uid) } catch (_: Exception) {
            return ResponseEntity.notFound().build()
        }
        if (user.has2fa) return ResponseEntity.status(HttpStatus.CONFLICT).build()
        if (request.secret2FA.isBlank() || !totpService.verifyCode(request.secret2FA, request.code)) {
            return ResponseEntity.badRequest().body("Invalid authenticator verification")
        }
        val updated = usersRepository.save(user.copy(
            has2fa = true, secret2FA = request.secret2FA, updatedDate = LocalDateTime.now()
        ))
        return ResponseEntity.ok(mapOf("uid" to updated.uid, "has2fa" to updated.has2fa))
    }


@PutMapping("/disable2FA/{uid}")
fun disable2FA(
    @PathVariable uid: String,
    @RequestBody request: Disable2FARequest,
    @AuthenticationPrincipal jwt: Jwt
): ResponseEntity<Any> {

    // Only the authenticated account owner may disable 2FA.
    if (!isOwnUser(uid, jwt)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
    }

    val user = try {
        usersRepository.findOneByUid(uid)
    } catch (_: Exception) {
        return ResponseEntity.notFound().build()
    }

    if (!user.has2fa || user.secret2FA.isNullOrBlank()) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body("2FA is not enabled for this account")
    }

    // Require a fresh authenticator verification code.
    if (!totpService.verifyCode(user.secret2FA, request.code)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body("Invalid 2FA verification code")
    }

    // Disable 2FA only after successful verification.
    val updated = usersRepository.save(
        user.copy(
            has2fa = false,
            secret2FA = null,
            updatedDate = LocalDateTime.now()
        )
    )

    return ResponseEntity.ok(
        mapOf(
            "uid" to updated.uid,
            "has2fa" to updated.has2fa
        )
    )
}


    @PostMapping
    fun createUser(@RequestBody request: UserRequest): ResponseEntity<Any> {
        val existing = try { usersRepository.findOneByEmail(request.email) } catch (_: Exception) { null }
        if (existing != null) return ResponseEntity.status(HttpStatus.CONFLICT).build()
        val (hashedPassword, salt) = hashAndSaltPassword(request.password)
        val user = usersRepository.save(User(
            name = request.name, email = request.email, role = "Viewer",
            password = hashedPassword, salt = salt, isRegistered = true
        ))
        return ResponseEntity.status(HttpStatus.CREATED).body(safeUser(user))
    }

    @PutMapping("/{uid}")
    fun updateUser(
        @RequestBody request: NameEmailRequest,
        @PathVariable uid: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        if (!isOwnUser(uid, jwt)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val user = try { usersRepository.findOneByUid(uid) } catch (_: Exception) {
            return ResponseEntity.notFound().build()
        }
        val existing = try { usersRepository.findOneByEmail(request.email) } catch (_: Exception) { null }
        if (existing != null && existing.uid != uid) return ResponseEntity.status(HttpStatus.CONFLICT).build()
        val updated = usersRepository.save(user.copy(
            name = request.name, email = request.email, updatedDate = LocalDateTime.now()
        ))
        return ResponseEntity.ok(safeUser(updated))
    }

    // Password recovery is authorized by a single-use, expiring reset token.
    // No bearer JWT is required, but possession of the reset token is mandatory.
    @PutMapping("/reset-password/{uid}")
    fun updatePassword(
        @RequestBody request: PasswordRequest,
        @PathVariable uid: String
    ): ResponseEntity<Any> {
        val user = try { usersRepository.findOneByUid(uid) } catch (_: Exception) { null }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!validToken(user.passwordToken, request.resetPasswordToken) ||
            !validTokenExpiration(user.passwordTokenExpiration)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        if (request.newPassword.isBlank()) return ResponseEntity.badRequest().build()
        if (user.password != null && BCrypt.checkpw(request.newPassword, user.password)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
        val (hashedPassword, salt) = hashAndSaltPassword(request.newPassword)
        usersRepository.save(user.copy(
            password = hashedPassword,
            salt = salt,
            passwordToken = null,
            passwordTokenExpiration = LocalDateTime.now(),
            updatedDate = LocalDateTime.now()
        ))
        return ResponseEntity.noContent().build()
    }

    // Public password recovery using email and a single-use reset token.
    @PutMapping("/reset-password-by-email")
    fun resetPasswordByEmail(
        @RequestBody request: EmailPasswordResetRequest
    ): ResponseEntity<Any> {
        val user = try {
            usersRepository.findOneByEmail(request.email)
        } catch (_: Exception) {
            null
        } ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        if (!validToken(user.passwordToken, request.resetPasswordToken) ||
            !validTokenExpiration(user.passwordTokenExpiration)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        if (request.newPassword.isBlank()) {
            return ResponseEntity.badRequest().build()
        }

        if (user.password != null &&
            BCrypt.checkpw(request.newPassword, user.password)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

        val (hashedPassword, salt) = hashAndSaltPassword(request.newPassword)

        usersRepository.save(user.copy(
            password = hashedPassword,
            salt = salt,
            passwordToken = null,
            passwordTokenExpiration = LocalDateTime.now(),
            updatedDate = LocalDateTime.now()
        ))

        return ResponseEntity.noContent().build()
    }

    fun hashAndSaltPassword(plainTextPassword: String, salt: String? = null): Pair<String, String> {
        val generatedSalt = salt ?: BCrypt.gensalt()
        return Pair(BCrypt.hashpw(plainTextPassword, generatedSalt), generatedSalt)
    }

    @PostMapping("/authenticate-token")
    fun authenticateToken(@RequestBody request: AuthenticationRequest): ResponseEntity<Any> {
        val user = try { usersRepository.findOneByEmail(request.email) } catch (_: Exception) { null }
        if (user == null || !validToken(user.passwordToken, request.resetPasswordToken) ||
            !validTokenExpiration(user.passwordTokenExpiration)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        return ResponseEntity.ok(mapOf("valid" to true))
    }

    @PostMapping("/generate-token")
    fun generatePasswordToken(@RequestBody request: NameEmailRequest): ResponseEntity<Any> {
        val user = try { usersRepository.findOneByEmail(request.email) } catch (_: Exception) { null }
        if (user != null) {
            val token = getRandomString(48)
            val updated = usersRepository.save(user.copy(
                passwordToken = token,
                passwordTokenExpiration = LocalDateTime.now().plusHours(1),
                updatedDate = LocalDateTime.now()
            ))
            emailService.sendResetPasswordEmail(updated.name, updated.email, token)
        }
        return ResponseEntity.ok(mapOf("message" to "If the account exists, reset instructions have been sent"))
    }

    @PostMapping("/update-session-duration")
    fun updateSessionDuration(
        @RequestBody request: UpdateSessionDurationRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val user = try { usersRepository.findOneByUid(jwt.subject) } catch (_: Exception) { null }
            ?: return ResponseEntity.notFound().build()
        if (user.email != request.email) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val updated = usersRepository.save(user.copy(
            sessionDuration = request.sessionDuration, updatedDate = LocalDateTime.now()
        ))
        return ResponseEntity.ok(safeUser(updated))
    }

    @PostMapping("/update-session-timeout-status")
    fun updateSessionTimeoutStatus(
        @RequestBody request: NameEmailRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val user = try { usersRepository.findOneByUid(jwt.subject) } catch (_: Exception) { null }
            ?: return ResponseEntity.notFound().build()
        if (user.email != request.email) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val updated = usersRepository.save(user.copy(
            hasSessionTimeout = !user.hasSessionTimeout, updatedDate = LocalDateTime.now()
        ))
        return ResponseEntity.ok(safeUser(updated))
    }

    @GetMapping("/count-session-timeout")
    fun countUsersBySessionTimeout(): ResponseEntity<Map<String, Long>> = ResponseEntity.ok(
        mapOf(
            "timeoutEnabledUsers" to usersRepository.countByHasSessionTimeout(true),
            "nonTimeoutEnabledUsers" to usersRepository.countByHasSessionTimeout(false)
        )
    )

    fun getRandomString(length: Int): String {
        val bytes = ByteArray((length * 3 + 3) / 4)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).take(length)
    }

    fun validToken(userToken: String?, token: String): Boolean =
        !userToken.isNullOrBlank() && token.isNotBlank() &&
            MessageDigest.isEqual(userToken.toByteArray(Charsets.UTF_8), token.toByteArray(Charsets.UTF_8))

    fun validTokenExpiration(tokenExpiration: LocalDateTime): Boolean = tokenExpiration.isAfter(LocalDateTime.now())

    @DeleteMapping("/{uid}")
    fun deleteUser(@PathVariable uid: String, @AuthenticationPrincipal jwt: Jwt): ResponseEntity<Any> {
        if (!isOwnUser(uid, jwt)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val user = try { usersRepository.findOneByUid(uid) } catch (_: Exception) { null }
            ?: return ResponseEntity.notFound().build()
        usersRepository.delete(user)
        return ResponseEntity.noContent().build()
    }

    fun getUsers(): List<User> = usersRepository.findAll()
    fun userExist(email: String): Boolean = usersRepository.findAll().any { it.email == email }
    fun checkAdminStatus(email: String): Boolean = usersRepository.findAllByIsAdmin(true).any { it.email == email }
}
