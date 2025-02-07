package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.Forex
import sg.com.quantai.middleware.repositories.AssetForexRepository
import sg.com.quantai.middleware.requests.ForexRequest

import java.math.BigDecimal
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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForexControllerTest 
@Autowired 
constructor(
    private val newForexRepository: AssetForexRepository,
    private val restTemplate: TestRestTemplate
) {
    @LocalServerPort protected var port: Int = 0

    @BeforeEach
    fun setUp() {
        newForexRepository.deleteAll()
    }

    private fun getRootUrl(): String? = "http://localhost:$port/forex"

    private fun prepareForexRequest(
        name: String = "United States Dollar",
        symbol: String = "USD",
        quantity: BigDecimal = BigDecimal(1),
        purchasePrice: BigDecimal= BigDecimal(20000),
    ) =
        ForexRequest(
            name= name,
            symbol= symbol,
            quantity = quantity,
            purchasePrice = purchasePrice,
        )
    
    private fun saveOneForex(
        name: String = "Test Forex",
        symbol: String = "TEST",
        quantity: BigDecimal = BigDecimal(2),
        purchasePrice: BigDecimal = BigDecimal(10),
)=  
    newForexRepository.save(
        Forex(
            name = name,
            symbol = symbol,
            quantity = quantity,
            purchasePrice = purchasePrice,
        )
    )

    // Create Functions
    @Test
    fun `should add forex`() {
        val forexRequest = prepareForexRequest()

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(forexRequest, HttpHeaders()),
                Forex::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body?.uid)
        assertEquals(forexRequest.name, response.body?.name)
        assertEquals(forexRequest.symbol, response.body?.symbol)
        assertEquals(forexRequest.quantity, response.body?.quantity)
        assertEquals(forexRequest.purchasePrice, response.body?.purchasePrice)
    }

    @Test
    fun `should accept Empty Name`() {
        val forexRequest = prepareForexRequest(name="")

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(forexRequest, HttpHeaders()),
                Forex::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body?.uid)
        assertEquals(forexRequest.name, response.body?.name)
        assertEquals(forexRequest.symbol, response.body?.symbol)
        assertEquals(forexRequest.quantity, response.body?.quantity)
        assertEquals(forexRequest.purchasePrice, response.body?.purchasePrice)
    }

    @Test
    fun `should accept Empty Symbol`() {
        val forexRequest = prepareForexRequest(symbol="")

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(forexRequest, HttpHeaders()),
                Forex::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body?.uid)
        assertEquals(forexRequest.name, response.body?.name)
        assertEquals(forexRequest.symbol, response.body?.symbol)
        assertEquals(forexRequest.quantity, response.body?.quantity)
        assertEquals(forexRequest.purchasePrice, response.body?.purchasePrice)
    }

    @Test
    fun `should reject bad request - Empty Name and Symbol`() {
        val forexRequest = prepareForexRequest(name="",symbol="")

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(forexRequest, HttpHeaders()),
                Forex::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - quantity negative`() {
        val forexRequest = prepareForexRequest(quantity = BigDecimal(-0.5))

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(forexRequest, HttpHeaders()),
                Forex::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - purchase price 0`() {
        val forexRequest = prepareForexRequest(purchasePrice = BigDecimal(0))
        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(forexRequest, HttpHeaders()),
                Forex::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - purchase price negative`() {
        val forexRequest = prepareForexRequest(purchasePrice = BigDecimal(-0.5))

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(forexRequest, HttpHeaders()),
                Forex::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }
    
    // Read Functions
    @Test
    fun `should retrieve all forexs`() {
        saveOneForex()
        saveOneForex(name = "Test2")
        saveOneForex(name = "Test3")
        var response = restTemplate.getForEntity(getRootUrl(), List::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(3, response.body?.size)
        val names = response.body?.map { (it as Map<*, *>)["name"] } ?: emptyList()
        assertTrue(names.containsAll(listOf("Test Forex", "Test2", "Test3")))
    }

    @Test
    fun `should get a single forex by uid`() {
        saveOneForex()
        saveOneForex(name = "Test2")
        val savedId = saveOneForex(name = "Test3").uid
        var response = restTemplate.getForEntity(getRootUrl()+"/$savedId", Forex::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("Test3", response.body?.name)
    }

    @Test
    fun `should get a forex by name`() {
        saveOneForex(symbol = "Test1", name = "Forex A")
        saveOneForex(symbol = "Test2", name = "Forex A")
        saveOneForex(symbol = "Test3", name = "Forex B")

        val name = "Forex A"
        val response = restTemplate.getForEntity(getRootUrl() + "/name/$name", List::class.java)

        assertEquals(200, response.statusCode.value())

        val symbols = response.body?.map { (it as Map<*, *>)["symbol"] } ?: emptyList()
        assertTrue(symbols.containsAll(listOf("Test1","Test2")) && symbols.size == 2)
    }

    @Test
    fun `should get a forex by symbol`() {
        saveOneForex(symbol = "Test2", name = "Forex A")
        saveOneForex(symbol = "Test2", name = "Forex B")
        saveOneForex(symbol = "Test3", name = "Forex C")

        val symbol = "Test2"
        val response = restTemplate.getForEntity(getRootUrl() + "/symbol/$symbol", List::class.java)

        assertEquals(200, response.statusCode.value())

        val names = response.body?.map { (it as Map<*, *>)["name"] } ?: emptyList()
        assertTrue(names.containsAll(listOf("Forex A", "Forex B")) && names.size == 2)
    }

    @Test
    fun `should get quantity of a forex by name`() {
        saveOneForex(symbol = "Test1", name = "Forex A",quantity=BigDecimal(5))
        saveOneForex(symbol = "Test2", name = "Forex A",quantity=BigDecimal(5))
        saveOneForex(symbol = "Test3", name = "Forex B")

        val name = "Forex A"
        val response = restTemplate.getForEntity(getRootUrl() + "/quantity/$name", BigDecimal::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(BigDecimal(10), response.body)
    }

    @Test
    fun `should get quantity of a forex by symbol`() {
        saveOneForex(symbol = "Test2", name = "Forex A",quantity=BigDecimal(5))
        saveOneForex(symbol = "Test2", name = "Forex B",quantity=BigDecimal(5))
        saveOneForex(symbol = "Test3", name = "Forex C")

        val symbol = "Test2"
        val response = restTemplate.getForEntity(getRootUrl() + "/quantity/$symbol", BigDecimal::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(BigDecimal(10), response.body)
    }

    // delete

    @Test
    fun `should delete forex`() {
        val savedId = saveOneForex().uid

        val response = restTemplate.exchange(
            getRootUrl() + "/delete/$savedId",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )

        assertEquals("Deleted forex $savedId", response.body)
    }
}