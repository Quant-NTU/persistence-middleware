package sg.com.quantai.middleware

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@SpringBootApplication
//@EntityScan(basePackages = ["sg.com.quantai.middleware.data.jpa", "sg.com.quantai.middleware.data.mongo"])
@EnableJpaRepositories(basePackages = ["sg.com.quantai.middleware.repositories.jpa"])
@EnableMongoRepositories(basePackages = ["sg.com.quantai.middleware.repositories.mongo"])
class MiddlewareApplication {
	fun main(args: Array<String>) {
		runApplication<MiddlewareApplication>(*args)
	}
}

