package sg.com.quantai.middleware.configs

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import org.mindrot.jbcrypt.BCrypt

@Component
class DataSeeder(
    private val userRepository: UserRepository
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        // Only seed if NO admin exists yet
        val adminCount = userRepository.countByIsAdmin(true)
        if (adminCount == 0L) {
            val salt           = BCrypt.gensalt()
            val hashedPassword = BCrypt.hashpw("admin123", salt)

            userRepository.save(
                User(
                    name        = "System Admin",
                    email       = "admin@quantai.sg",
                    password    = hashedPassword,
                    salt        = salt,
                    isRegistered = true,
                    isAdmin     = true,
                    role        = "Admin",
                )
            )
            println("[DataSeeder] Default admin created: admin@quantai.sg / admin123")
            println("[DataSeeder] ⚠ Change this password immediately after first login!")
        }
    }
}