package sg.com.quantai.middleware.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate

@SpringBootTest
class MongoIsolationTest(
    @Autowired private val mongoTemplate: MongoTemplate
) {
    @Test
    fun `tests must use isolated MongoDB database`() {
        assertEquals(
            "quantai_rbac_automated_test",
            mongoTemplate.db.name,
            "Refusing to use a non-test MongoDB database"
        )
    }
}
