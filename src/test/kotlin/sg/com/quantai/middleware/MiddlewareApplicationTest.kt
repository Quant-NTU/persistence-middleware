package sg.com.quantai.middleware

import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@SpringBootTest
@EntityScan(basePackages = ["sg.com.quantai.middleware.data.jpa", "sg.com.quantai.middleware.data.mongo"])
@EnableJpaRepositories(basePackages = ["sg.com.quantai.middleware.repositories.jpa"])
@EnableMongoRepositories(basePackages = ["sg.com.quantai.middleware.repositories.mongo"])
class MiddlewareApplicationTest {

	@Test
	fun contextLoads() {
		
	}

}