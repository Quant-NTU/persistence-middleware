package sg.com.quantai.middleware.configs

import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.repositories.mongo.UserRepository

@Component
@Profile("local", "dev")
class DataSeeder(private val userRepository: UserRepository) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DataSeeder::class.java)

    override fun run(args: ApplicationArguments) {
        if (userRepository.countByIsAdmin(true) == 0L) {
            val salt           = BCrypt.gensalt()
            val hashedPassword = BCrypt.hashpw("admin123", salt)
            userRepository.save(User(
                name         = "System Admin",
                email        = "admin@quantai.sg",
                password     = hashedPassword,
                salt         = salt,
                isRegistered = true,
                isAdmin      = true,
                role         = "Admin",
            ))
            log.warn("[DataSeeder] Default admin created: admin@quantai.sg — change password immediately")
        }
    }
}
