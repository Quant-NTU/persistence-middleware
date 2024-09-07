package sg.com.quantai.middleware.controllers

import java.time.LocalDateTime
import java.time.ZoneOffset
import sg.com.quantai.middleware.data.User
import sg.com.quantai.middleware.repositories.UserRepository
import sg.com.quantai.middleware.requests.AuthenticationRequest
import sg.com.quantai.middleware.requests.LoginRequest
import sg.com.quantai.middleware.requests.NameEmailRequest
import sg.com.quantai.middleware.requests.PasswordRequest
import sg.com.quantai.middleware.requests.UserRequest
import sg.com.quantai.middleware.requests.GoogleConfirmationRequest
import sg.com.quantai.middleware.requests.AccountConfirmationRequest
import sg.com.quantai.middleware.requests.AdminRequest
import sg.com.quantai.middleware.requests.Enable2FARequest
import sg.com.quantai.middleware.requests.UpdateSessionDurationRequest
import sg.com.quantai.middleware.mailsender.config.EmailServiceImpl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.mindrot.jbcrypt.BCrypt

@RestController
@RequestMapping("/users")
class UserController(
        private val usersRepository: UserRepository,
        private val emailService: EmailServiceImpl,
) {

    // Retrieve all the users
    @GetMapping
    fun getAllUsers(): ResponseEntity<List<User>> {
        val users = usersRepository.findAll()
        return ResponseEntity.ok(users)
    }

    // Get a single user by id
    @GetMapping("/{uid}")
    fun getOneUser(@PathVariable("uid") uid: String): ResponseEntity<User> {
        val user = usersRepository.findOneByUid(uid)
        return ResponseEntity.ok(user)
    }

    // Login function
    @PostMapping("/signin")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<User> {
        if (userExist(request.email)) {
            val user = usersRepository.findOneByEmail(request.email)
            val (hashedPassword, salt) = hashAndSaltPassword(request.password, user.salt)
            if (user.password == hashedPassword) {
                if (!user.has2fa) {
                    val loginUser =
                    usersRepository.save(
                        User(
                                name = user.name,
                                email = user.email,
                                password = user.password,
                                salt = user.salt,
                                passwordToken = user.passwordToken,
                                passwordTokenExpiration = user.passwordTokenExpiration,
                                createdDate = user.createdDate,
                                updatedDate = user.updatedDate,
                                isRegistered = user.isRegistered,
                                isGoogleLogin = user.isGoogleLogin,
                                isAdmin = user.isAdmin,
                                has2fa = user.has2fa,
                                secret2FA = user.secret2FA,
                                lastLogin = LocalDateTime.now(),
                                hasSessionTimeout = user.hasSessionTimeout,
                                sessionDuration = user.sessionDuration,
                                _id = user._id,
                                uid = user.uid
                        )
                    )
                return ResponseEntity(loginUser, HttpStatus.OK)
                } else {
                    return ResponseEntity(user, HttpStatus.ACCEPTED)
                }
            } else {
                return ResponseEntity(HttpStatus.UNAUTHORIZED)
            }
        } else {
            return ResponseEntity(HttpStatus.UNAUTHORIZED)
        }
    }

    @PostMapping("/login/oauth2/google")
    fun googleOAuthLogin(@RequestBody request: NameEmailRequest): ResponseEntity<User> {
        if (userExist(request.email)) {
            val user = usersRepository.findOneByEmail(request.email)
            if (user.isGoogleLogin){
                if (!user.has2fa) {
                    val googleLoginUser =
                    usersRepository.save(
                        User(
                                name = user.name,
                                email = user.email,
                                password = user.password,
                                salt = user.salt,
                                passwordToken = user.passwordToken,
                                passwordTokenExpiration = user.passwordTokenExpiration,
                                createdDate = user.createdDate,
                                updatedDate = user.updatedDate,
                                isRegistered = user.isRegistered,
                                isGoogleLogin = user.isGoogleLogin,
                                isAdmin = user.isAdmin,
                                has2fa = user.has2fa,
                                secret2FA = user.secret2FA,
                                lastLogin = LocalDateTime.now(),
                                hasSessionTimeout = user.hasSessionTimeout,
                                sessionDuration = user.sessionDuration,
                                _id = user._id,
                                uid = user.uid
                        )
                    )
                    return ResponseEntity(googleLoginUser, HttpStatus.OK)
                } else {
                    return ResponseEntity(user, HttpStatus.ACCEPTED)
                }
            } else {
                return ResponseEntity(user, HttpStatus.ACCEPTED)
            }
        } else {
            val user =
                usersRepository.save(
                    User(
                            name = request.name,
                            email = request.email,
                            isGoogleLogin = true,
                            lastLogin = LocalDateTime.now()
                    )
                )
            return ResponseEntity(user, HttpStatus.OK)
        }
    }

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

    @PutMapping("/admin/{email}")
    fun promoteToAdminUser(
        @PathVariable email: String,
        @RequestBody request: AdminRequest
    ): ResponseEntity<User> {
        val user = usersRepository.findOneByUid(request.requesterUid)
        val adminCount = usersRepository.countByIsAdmin(true)
        if (!user.isAdmin && adminCount != 0L) {
            return ResponseEntity(HttpStatus.UNAUTHORIZED)
        }
        if(userExist(email)){
            if (checkAdminStatus(email)){
                return ResponseEntity(HttpStatus.EXPECTATION_FAILED)
            }
            val user = usersRepository.findOneByEmail(email)
            val newAdmin =
                usersRepository.save(
                        User(
                            name = user.name,
                            email = user.email,
                            password = user.password,
                            salt = user.salt,
                            passwordToken = user.passwordToken,
                            passwordTokenExpiration = user.passwordTokenExpiration,
                            createdDate = user.createdDate,
                            updatedDate = LocalDateTime.now(),
                            isRegistered = user.isRegistered,
                            isGoogleLogin = user.isGoogleLogin,
                            isAdmin = true,
                            has2fa = user.has2fa,
                            secret2FA = user.secret2FA,
                            lastLogin = user.lastLogin,
                            hasSessionTimeout = user.hasSessionTimeout,
                            sessionDuration = user.sessionDuration,
                            _id = user._id,
                            uid = user.uid
                        )
                )
            return ResponseEntity(newAdmin, HttpStatus.OK)
        } else {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    @DeleteMapping("/admin/{email}")
    fun demoteFromAdminUser(
        @PathVariable email: String,
        @RequestBody request: AdminRequest
    ): ResponseEntity<User> {
        val user = usersRepository.findOneByUid(request.requesterUid)
        if (!user.isAdmin) {
            return ResponseEntity(HttpStatus.UNAUTHORIZED)
        }
        if(userExist(email)){
            if (!checkAdminStatus(email)){
                return ResponseEntity(HttpStatus.EXPECTATION_FAILED)
            }
            val user = usersRepository.findOneByEmail(email)
            val newAdmin =
                usersRepository.save(
                        User(
                            name = user.name,
                            email = user.email,
                            password = user.password,
                            salt = user.salt,
                            passwordToken = user.passwordToken,
                            passwordTokenExpiration = user.passwordTokenExpiration,
                            createdDate = user.createdDate,
                            updatedDate = LocalDateTime.now(),
                            isRegistered = user.isRegistered,
                            isGoogleLogin = user.isGoogleLogin,
                            isAdmin = false,
                            has2fa = user.has2fa,
                            secret2FA = user.secret2FA,
                            lastLogin = user.lastLogin,
                            hasSessionTimeout = user.hasSessionTimeout,
                            sessionDuration = user.sessionDuration,
                            _id = user._id,
                            uid = user.uid
                        )
                )
            return ResponseEntity(newAdmin, HttpStatus.OK)
        } else {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    @PostMapping("/google-confirmation")
    fun googleConfirmation(@RequestBody request: GoogleConfirmationRequest): ResponseEntity<User> {
        if (request.oauthEmail == request.registrationEmail) {
            val user = usersRepository.findOneByEmail(request.oauthEmail)
            val updatedUser =
                usersRepository.save(
                        User(
                                name = request.name,
                                email = request.oauthEmail,
                                password = request.password,
                                salt = request.salt,
                                passwordToken = user.passwordToken,
                                passwordTokenExpiration = user.passwordTokenExpiration,
                                createdDate = user.createdDate,
                                updatedDate = LocalDateTime.now(),
                                isRegistered = true,
                                isGoogleLogin = user.isGoogleLogin,
                                isAdmin = user.isAdmin,
                                has2fa = user.has2fa,
                                secret2FA = user.secret2FA,
                                lastLogin = LocalDateTime.now(),
                                hasSessionTimeout = user.hasSessionTimeout,
                                sessionDuration = user.sessionDuration,
                                _id = user._id,
                                uid = user.uid
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

    @PostMapping("/account-confirmation")
    fun accountConfirmation(@RequestBody request: AccountConfirmationRequest): ResponseEntity<User> {
        if (request.registrationEmail == request.oauthEmail) {
            val user = usersRepository.findOneByEmail(request.registrationEmail)
            val (hashedPassword, salt) = hashAndSaltPassword(request.password, user.salt)
            if (user.password == hashedPassword) {
                val updatedUser =
                usersRepository.save(
                        User(
                                name = user.name,
                                email = user.email,
                                password = user.password,
                                salt = user.salt,
                                passwordToken = user.passwordToken,
                                passwordTokenExpiration = user.passwordTokenExpiration,
                                createdDate = user.createdDate,
                                updatedDate = LocalDateTime.now(),
                                isRegistered = user.isRegistered,
                                isGoogleLogin = true,
                                isAdmin = user.isAdmin,
                                has2fa = user.has2fa,
                                secret2FA = user.secret2FA,
                                lastLogin = LocalDateTime.now(),
                                hasSessionTimeout = user.hasSessionTimeout,
                                sessionDuration = user.sessionDuration,
                                _id = user._id,
                                uid = user.uid
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

    @PostMapping("/enable2FA/{uid}")
    fun enable2FA(
        @RequestBody request: Enable2FARequest,
        @PathVariable("uid") uid: String
    ): ResponseEntity<User> {
        val user = usersRepository.findOneByUid(uid)
        if (user.has2fa) {
            return ResponseEntity(HttpStatus.EXPECTATION_FAILED)
        } else {
            val updatedUser =
                usersRepository.save(
                        User(
                                name = user.name,
                                email = user.email,
                                password = user.password,
                                salt = user.salt,
                                passwordToken = user.passwordToken,
                                passwordTokenExpiration = user.passwordTokenExpiration,
                                createdDate = user.createdDate,
                                updatedDate = LocalDateTime.now(),
                                isRegistered = user.isRegistered,
                                isGoogleLogin = user.isGoogleLogin,
                                isAdmin = user.isAdmin,
                                has2fa = true,
                                secret2FA = request.secret2FA,
                                lastLogin = user.lastLogin,
                                hasSessionTimeout = user.hasSessionTimeout,
                                sessionDuration = user.sessionDuration,
                                _id = user._id,
                                uid = user.uid
                        )
                )
            return ResponseEntity(updatedUser, HttpStatus.OK)
        }
    }

    @PutMapping("/disable2FA/{uid}")
    fun disable2FA(
        @PathVariable("uid") uid: String
    ): ResponseEntity<User> {
        val user = usersRepository.findOneByUid(uid)
        if (!user.has2fa) {
            return ResponseEntity(HttpStatus.EXPECTATION_FAILED)
        } else {
            val updatedUser =
                usersRepository.save(
                        User(
                                name = user.name,
                                email = user.email,
                                password = user.password,
                                salt = user.salt,
                                passwordToken = user.passwordToken,
                                passwordTokenExpiration = user.passwordTokenExpiration,
                                createdDate = user.createdDate,
                                updatedDate = LocalDateTime.now(),
                                isRegistered = user.isRegistered,
                                isGoogleLogin = user.isGoogleLogin,
                                isAdmin = user.isAdmin,
                                has2fa = false,
                                secret2FA = user.secret2FA,
                                lastLogin = user.lastLogin,
                                hasSessionTimeout = user.hasSessionTimeout,
                                sessionDuration = user.sessionDuration,
                                _id = user._id,
                                uid = user.uid
                        )
                )
            return ResponseEntity(updatedUser, HttpStatus.OK)
        }
    }

    // Register function
    @PostMapping
    fun createUser(@RequestBody request: UserRequest): ResponseEntity<User> {
        if (userExist(request.email)) {
            val existingUser = usersRepository.findOneByEmail(request.email)
            if (existingUser.isRegistered) {
                return ResponseEntity(HttpStatus.NO_CONTENT)
            } else {
                val (hashedPassword, salt) = hashAndSaltPassword(request.password)
                val updatedExistingUser = User(
                    name = request.name,
                    email = request.email,
                    password = hashedPassword,
                    salt = salt,
                    passwordToken = existingUser.passwordToken,
                    passwordTokenExpiration = existingUser.passwordTokenExpiration,
                    createdDate = existingUser.createdDate,
                    updatedDate = LocalDateTime.now(),
                    isRegistered = true,
                    isGoogleLogin = existingUser.isGoogleLogin,
                    isAdmin = false,
                    has2fa = existingUser.has2fa,
                    secret2FA = existingUser.secret2FA,
                    lastLogin = existingUser.lastLogin,
                    hasSessionTimeout = existingUser.hasSessionTimeout,
                    sessionDuration = existingUser.sessionDuration,
                    _id = existingUser._id,
                    uid = existingUser.uid
                )
                return ResponseEntity(updatedExistingUser, HttpStatus.ACCEPTED)
            }
        } else {
            val (hashedPassword, salt) = hashAndSaltPassword(request.password)
            val user =
                    usersRepository.save(
                            User(
                                    name = request.name,
                                    email = request.email,
                                    password = hashedPassword,
                                    salt = salt,
                                    isRegistered = true
                            )
                    )
            return ResponseEntity(user, HttpStatus.CREATED)
        }
    }

    // Update function
    // TODO: Allow to update only name and e-mail
    @PutMapping("/{uid}")
    fun updateUser(
            @RequestBody request: NameEmailRequest,
            @PathVariable("uid") uid: String
    ): ResponseEntity<User> {
        val user = usersRepository.findOneByUid(uid)
        if (userExist(request.email) && (request.email != user.email)){
            return ResponseEntity(HttpStatus.FORBIDDEN)
        } else{
            val updatedUser =
                usersRepository.save(
                        User(
                                name = request.name,
                                email = request.email,
                                password = user.password,
                                salt = user.salt,
                                passwordToken = user.passwordToken,
                                passwordTokenExpiration = user.passwordTokenExpiration,
                                createdDate = user.createdDate,
                                updatedDate = LocalDateTime.now(),
                                isRegistered = user.isRegistered,
                                isGoogleLogin = user.isGoogleLogin,
                                isAdmin = user.isAdmin,
                                has2fa = user.has2fa,
                                secret2FA = user.secret2FA,
                                lastLogin = user.lastLogin,
                                hasSessionTimeout = user.hasSessionTimeout,
                                sessionDuration = user.sessionDuration,
                                _id = user._id,
                                uid = user.uid
                        )
                )
        return ResponseEntity.ok(updatedUser)
        }
        
    }

    // Update password
    @PutMapping("/reset-password/{uid}")
    fun updatePassword(
            @RequestBody request: PasswordRequest,
            @PathVariable("uid") uid: String
    ): ResponseEntity<User> {
        val user = usersRepository.findOneByUid(uid)
        val (hashedPassword, salt) = hashAndSaltPassword(request.newPassword, user.salt)
        if (user.password != hashedPassword) {
            val updatedUser =
                    usersRepository.save(
                            User(
                                    name = user.name,
                                    email = user.email,
                                    password = hashedPassword,
                                    salt = salt,
                                    passwordToken = null,
                                    passwordTokenExpiration = LocalDateTime.now(),
                                    createdDate = user.createdDate,
                                    updatedDate = LocalDateTime.now(),
                                    isRegistered = user.isRegistered,
                                    isGoogleLogin = user.isGoogleLogin,
                                    isAdmin = user.isAdmin,
                                    has2fa = user.has2fa,
                                    secret2FA = user.secret2FA,
                                    lastLogin = user.lastLogin,
                                    hasSessionTimeout = user.hasSessionTimeout,
                                    sessionDuration = user.sessionDuration,
                                    _id = user._id,
                                    uid = user.uid
                            )
                    )
            return ResponseEntity.ok(updatedUser)
        } else {
            return ResponseEntity(HttpStatus.NOT_MODIFIED)
        }
    }

    fun hashAndSaltPassword(plainTextPassword: String, salt: String? = null): Pair<String, String> {

        val generatedSalt = salt ?: BCrypt.gensalt()
        val hashedPassword = BCrypt.hashpw(plainTextPassword, generatedSalt)
    
        return Pair(hashedPassword, generatedSalt)
    }

    // ResetPassword Function for forget password
    @PostMapping("/authenticate-token")
    fun authenticateToken(@RequestBody request: AuthenticationRequest): ResponseEntity<User> {
        // val user = usersRepository.findOneById(request.id) --> to identify why ID changes
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

    @PostMapping("/generate-token")
    fun generatePasswordToken(@RequestBody request: NameEmailRequest): ResponseEntity<User> {
        if (userExist(request.email)) {
            val user = usersRepository.findOneByEmail(request.email)
            val updatedUser =
                    usersRepository.save(
                            User(
                                    name = user.name,
                                    email = user.email,
                                    password = user.password,
                                    salt = user.salt,
                                    passwordToken = getRandomString(30),
                                    passwordTokenExpiration = LocalDateTime.now().plusHours(1),
                                    createdDate = user.createdDate,
                                    updatedDate = LocalDateTime.now(),
                                    isRegistered = user.isRegistered,
                                    isGoogleLogin = user.isGoogleLogin,
                                    isAdmin = user.isAdmin,
                                    has2fa = user.has2fa,
                                    secret2FA = user.secret2FA,
                                    lastLogin = user.lastLogin,
                                    hasSessionTimeout = user.hasSessionTimeout,
                                    sessionDuration = user.sessionDuration,
                                    _id = user._id,
                                    uid = user.uid
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

    @PostMapping("/update-session-duration")
    fun updateSessionDuration(
        @RequestBody request: UpdateSessionDurationRequest
    ): ResponseEntity<User> {
        if (userExist(request.email)) {
            val user = usersRepository.findOneByEmail(request.email)
            val updatedUser =
                    usersRepository.save(
                            User(
                                    name = user.name,
                                    email = user.email,
                                    password = user.password,
                                    salt = user.salt,
                                    passwordToken = user.passwordToken,
                                    passwordTokenExpiration = user.passwordTokenExpiration,
                                    createdDate = user.createdDate,
                                    updatedDate = LocalDateTime.now(),
                                    isRegistered = user.isRegistered,
                                    isGoogleLogin = user.isGoogleLogin,
                                    isAdmin = user.isAdmin,
                                    has2fa = user.has2fa,
                                    secret2FA = user.secret2FA,
                                    lastLogin = user.lastLogin,
                                    hasSessionTimeout = user.hasSessionTimeout,
                                    sessionDuration = request.sessionDuration,
                                    _id = user._id,
                                    uid = user.uid
                            )
                    )
            return ResponseEntity(updatedUser, HttpStatus.OK)
        } else {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

    @PostMapping("/update-session-timeout-status")
    fun updateSessionTimeoutStatus(
        @RequestBody request: NameEmailRequest
    ): ResponseEntity<User> {
        if (userExist(request.email)) {
            val user = usersRepository.findOneByEmail(request.email)
            val updatedUser =
                    usersRepository.save(
                            User(
                                    name = user.name,
                                    email = user.email,
                                    password = user.password,
                                    salt = user.salt,
                                    passwordToken = user.passwordToken,
                                    passwordTokenExpiration = user.passwordTokenExpiration,
                                    createdDate = user.createdDate,
                                    updatedDate = LocalDateTime.now(),
                                    isRegistered = user.isRegistered,
                                    isGoogleLogin = user.isGoogleLogin,
                                    isAdmin = user.isAdmin,
                                    has2fa = user.has2fa,
                                    secret2FA = user.secret2FA,
                                    lastLogin = user.lastLogin,
                                    hasSessionTimeout = !user.hasSessionTimeout,
                                    sessionDuration = user.sessionDuration,
                                    _id = user._id,
                                    uid = user.uid
                            )
                    )
            return ResponseEntity(updatedUser, HttpStatus.OK)
        } else {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

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

    fun getRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length).map { allowedChars.random() }.joinToString("")
    }

    fun validToken(userToken: String?, token: String): Boolean {
        return userToken == token
    }

    fun validTokenExpiration(tokenExpiration: LocalDateTime): Boolean {
        return tokenExpiration > LocalDateTime.now()
    }

    // Delete Function
    @DeleteMapping("/{uid}")
    fun deleteUser(@PathVariable("uid") uid: String): ResponseEntity<Unit> {
        usersRepository.deleteById(uid)
        return ResponseEntity.noContent().build()
    }

    fun getUsers(): List<User> {
        val users = usersRepository.findAll()
        return users
    }

    // Check if user exists in the database
    fun userExist(email: String): Boolean {
        var exists: Boolean = false
        val ListOfUsers: List<User> = getUsers()
        for (u in ListOfUsers) {
            if (u.email == email) {
                exists = true
                break
            } else {
                exists = false
            }
        }
        return exists
    }

    fun checkAdminStatus(email: String): Boolean {
        val adminUsers: List<User> = usersRepository.findAllByIsAdmin(true)
        for (adminUser in adminUsers) {
            if (email == adminUser.email) {
                return true
            }
        }
        return false
    }

    // TODO
    // Create transactions -> SHOULD NOT EXIST. FUNCTIONALITY MUST BE ENTERED VIA THE TRANSACTION
    // CLASS
    // Retrieve transactions -> should return List<Transaction>, which can be empty
    // Update transactions -> SHOULD NOT EXIST. FUNCTIONALITY MUST BE ENTERED VIA THE TRANSACTION
    // CLASS
    // Delete transactiosn -> should delete all transactions and return an emptyu List<Transaction>
}