package sg.com.quantai.middleware.controllers.mongo

import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.data.mongo.Pipeline
import sg.com.quantai.middleware.data.mongo.Strategy
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import sg.com.quantai.middleware.repositories.mongo.PipelineRepository
import sg.com.quantai.middleware.repositories.mongo.StrategyRepository
import sg.com.quantai.middleware.requests.PipelineRequest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.LocalDateTime
import org.mindrot.jbcrypt.BCrypt
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension::class)
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PipelineControllerTest 
@Autowired 
constructor(
    private val pipelineRepository: PipelineRepository,
    private val strategyRepository: StrategyRepository,
    private val userRepository: UserRepository,
    private val restTemplate: TestRestTemplate
) {
    @LocalServerPort protected var port: Int = 0

    @BeforeEach
    fun setUp() {
        pipelineRepository.deleteAll()
        strategyRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun getRootUrl(): String? = "http://localhost:$port/pipelines"
    
    private fun saveOnePipeline(
        title: String = "Test Pipeline",
        description: String = "This is a test pipeline",
        owner: User = userRepository.save(User(name="Name", email="Email", password="Password", salt="Salt")),
        strategies: List<Strategy> = listOf(
            strategyRepository.save(
                Strategy(
                title = "Strategy 1",
                uid = "ID1",
                path = "Path1",
                owner = owner
            )
            ),
            strategyRepository.save(
                Strategy(
                title = "Strategy 2",
                uid = "ID2",
                path = "Path2",
                owner = owner
            )
            )
        ),
        portfolio: String = "",
        // portfolio: Portfolio = portfolioRepository.save(
        //     Portfolio(
        //         symbol="STOCK1",
        //         name="Stock 1",
        //         quantity=BigDecimal(1),
        //         price=BigDecimal(1.1),
        //         platform="Platform",
        //         owner = owner
        //     )
        // )
    )=  
        pipelineRepository.save(
            Pipeline(
                title = title,
                description = description,
                strategies = strategies,
                updatedDate = LocalDateTime.now(), 
                // portfolio = portfolio,
                owner = owner,
            )
        )

    private fun preparePipelineRequest(
        strategies_id: String = "",
        portfolio_id: String = "",
    ) =
        PipelineRequest(
            title= "Updated Title",
            description= "Updated Description",
            strategies_id = strategies_id,
            portfolio_id = portfolio_id,
        )

    private fun hashAndSaltPassword(plainTextPassword: String, salt: String? = null): Pair<String, String> {

        val generatedSalt = salt ?: BCrypt.gensalt()
        val hashedPassword = BCrypt.hashpw(plainTextPassword, generatedSalt)

        return Pair(hashedPassword, generatedSalt)
    }

    @Test
    fun `should return all pipelines from a user`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val (password2, salt2) = hashAndSaltPassword("Password2")
        val user1 = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val user2 = userRepository.save(User(name="Name2", email="Email2", password=password2, salt=salt2))
        val user1Id = user1.uid
        val user2Id = user2.uid

       // No pipelines for user 1
       var response =
        restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        // No pipelines for user 2
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOnePipeline(owner = user1)
        // 1 pipeline for user 1
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)
        // No pipelines for user 2
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOnePipeline(owner = user1)
        // 2 pipelines for user 1
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
        // No pipelines for user 2
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOnePipeline(owner = user2)
        // 2 pipelines for user 1
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
        // 1 pipeline for user 2
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)
    }

    @Test
    fun `should return single pipeline by id`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val user = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val userId = user.uid

        saveOnePipeline(owner=user)

        // val portfolio1 = portfolioRepository.save(
        //     Portfolio(
        //         symbol="STOCK2",
        //         name="Stock 2",
        //         quantity=BigDecimal(1),
        //         price=BigDecimal(1.1),
        //         platform="Platform2",
        //         owner = user
        //     )
        // )
        // val savedId = saveOnePipeline(portfolio=portfolio1,owner=user).uid
        val savedId = saveOnePipeline(owner=user).uid

        val response =
                restTemplate.getForEntity(getRootUrl() + "/user/$userId/$savedId", Pipeline::class.java)
        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        // assertEquals(portfolio1.uid, response.body?.portfolio?.uid)
        assertEquals(user.uid, response.body?.owner?.uid)
    }

    @Test
    fun `should create a pipeline using selected portfolio`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val user = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val userId = user.uid

        // val p1 = portfolioRepository.save(
        //     Portfolio(
        //         symbol="STOCK2",
        //         name="Stock 2",
        //         quantity=BigDecimal(1),
        //         price=BigDecimal(1.1),
        //         platform="Platform2",
        //         owner = user
        //     )
        // ).uid

        // val pipelineRequest = preparePipelineRequest(portfolio_id=p1)
        val pipelineRequest = preparePipelineRequest()

        val response =
            restTemplate.exchange(
                getRootUrl() + "/$userId",
                HttpMethod.POST,
                HttpEntity(pipelineRequest, HttpHeaders()),
                Pipeline::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertEquals(userId, response.body?.owner?.uid)
        // assertEquals(p1, response.body?.portfolio?.uid)

        assertEquals(pipelineRequest.title, response.body?.title)
        assertEquals(pipelineRequest.description, response.body?.description)
        assertEquals(emptyList<Strategy>(), response.body?.strategies)
    }

    @Test
    fun `should update an existing pipeline`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val user = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val userId = user.uid

        val pipeline = saveOnePipeline(owner=user)
        val pipelineId = pipeline.uid

        val s1 = Strategy(
            title = "Strategy new",
            uid = "IDnew",
            path = "Pathnew",
            owner = user
        )

        val s2 = Strategy(
            title = "Strategy new2",
            uid = "IDnew2",
            path = "Pathnew2",
            owner = user
        )

        val strat_id = strategyRepository.save(s1).uid
        val strat_id2 = strategyRepository.save(s2).uid
        val combinedStratIds = """[
        {"reactFlowId": "strategy-0", "strategyId":"$strat_id","prev":null,"next":"strategy-1"},
        {"reactFlowId": "strategy-1","strategyId":"$strat_id2","prev":"strategy-0","next":null},
        ]"""
        val expectedList = listOf(s1, s2)

        val pipelineRequest = preparePipelineRequest(strategies_id=combinedStratIds)

        val updatedResponse =
            restTemplate.exchange(
                getRootUrl() + "/user/$userId/$pipelineId",
                HttpMethod.PUT,
                HttpEntity(pipelineRequest, HttpHeaders()),
                Pipeline::class.java
            )
        val updatedPipeline = pipelineRepository.findOneByUid(pipelineId)

        // Check if the updatedPipeline is null
        // if (updatedPipeline.strategies == emptyList<NewStrategy>()) {
        //     println("Updated strategies should not be null")
        // }
        // else {
        //     expectedList.forEach { strategy ->
        //         println("Expected Strategy - Title: ${strategy.title}, UID: ${strategy.uid}, Path: ${strategy.path}, Owner: ${strategy.owner}")
        //     }        
        //     updatedPipeline.strategies?.forEach { strategy ->
        //         println("Updated Strategy - Title: ${strategy.title}, UID: ${strategy.uid}, Path: ${strategy.path}, Owner: ${strategy.owner}")
        //     }?: println("No strategies found in the updated pipeline")
        // }

        assertEquals(200, updatedResponse.statusCode.value())
        assertEquals(pipelineId, updatedPipeline.uid) // Id same
        assertEquals(pipeline.owner.uid,updatedPipeline.owner.uid) // Shouldn't allow change owner
        // assertEquals(pipeline.portfolio?.uid, updatedPipeline.portfolio?.uid) // Shouldn't allow change portfolio

        assertEquals(pipelineRequest.title, updatedPipeline.title)
        assertEquals(pipelineRequest.description, updatedPipeline.description)
        assertEquals(expectedList.map { it.uid }, updatedPipeline.strategies?.map { it.uid })
    }

    @Test
    fun `should return strategies of pipeline with duplicate strategies in correct order`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val user = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val userId = user.uid

        val pipeline = saveOnePipeline(owner=user)
        val pipelineId = pipeline.uid

        val s1 = Strategy(
            title = "Strategy new",
            uid = "IDnew",
            path = "Pathnew",
            owner = user
        )

        val s2 = Strategy(
            title = "Strategy new2",
            uid = "IDnew2",
            path = "Pathnew2",
            owner = user
        )

        val strat_id = strategyRepository.save(s1).uid
        val strat_id2 = strategyRepository.save(s2).uid
        val combinedStratIds = """[
        {"reactFlowId": "strategy-0", "strategyId":"$strat_id","prev":null,"next":"strategy-1"},
        {"reactFlowId": "strategy-1","strategyId":"$strat_id2","prev":"strategy-0","next":"strategy-2"},
        {"reactFlowId": "strategy-2","strategyId":"$strat_id2","prev":"strategy-1","next":"strategy-3"},
        {"reactFlowId": "strategy-3","strategyId":"$strat_id","prev":"strategy-2","next":null},
        ]"""
        val expectedList = listOf(s1, s2,s2,s1)

        val pipelineRequest = preparePipelineRequest(strategies_id=combinedStratIds)

        val updatedResponse =
            restTemplate.exchange(
                getRootUrl() + "/user/$userId/$pipelineId",
                HttpMethod.PUT,
                HttpEntity(pipelineRequest, HttpHeaders()),
                Pipeline::class.java
            )
        val updatedPipeline = pipelineRepository.findOneByUid(pipelineId)

        assertEquals(200, updatedResponse.statusCode.value())
        assertEquals(pipelineId, updatedPipeline.uid) // Id same
        assertEquals(pipeline.owner.uid,updatedPipeline.owner.uid) // Shouldn't allow change owner
        // assertEquals(pipeline.portfolio?.uid, updatedPipeline.portfolio?.uid) // Shouldn't allow change portfolio
        assertEquals(expectedList.map { it.uid }, updatedPipeline.strategies?.map { it.uid })
    }

    @Test
    fun `should delete an existing pipeline`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val (password2, salt2) = hashAndSaltPassword("Password2")
        val user1 = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val user2 = userRepository.save(User(name="Name2", email="Email2", password=password2, salt=salt2))
        val user1Id = user1.uid
        val user2Id = user2.uid

        val pipeline1_1_Id = saveOnePipeline(owner = user1).uid
        val pipeline1_2_Id = saveOnePipeline(owner = user1).uid
        val pipeline2_1_Id = saveOnePipeline(owner = user2).uid

        var response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(2, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)
        assertEquals(1, response.body?.size)

        // Delete pipeline1_1 for user 1
        val deleteResponse1 = restTemplate.exchange(
            getRootUrl() + "/user/$user1Id/$pipeline1_1_Id",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )

        assertEquals("Deleted pipeline $pipeline1_1_Id", deleteResponse1.body)
    
        response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(1, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)
        assertEquals(1, response.body?.size)

        // Delete pipeline2_1 for user 2
        val deleteResponse2 = restTemplate.exchange(
            getRootUrl() + "/user/$user2Id/$pipeline2_1_Id",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )

        assertEquals("Deleted pipeline $pipeline2_1_Id", deleteResponse2.body)
    
        response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(1, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)
        assertEquals(0, response.body?.size)

        // Delete pipeline1_2 for user 1
        val deleteResponse3 = restTemplate.exchange(
            getRootUrl() + "/user/$user1Id/$pipeline1_2_Id",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )
        assertEquals("Deleted pipeline $pipeline1_2_Id", deleteResponse3.body)
    
        response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(0, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)
        assertEquals(0, response.body?.size)
    }
}