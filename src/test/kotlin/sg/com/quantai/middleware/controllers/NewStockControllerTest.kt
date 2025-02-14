package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.NewStock
import sg.com.quantai.middleware.repositories.AssetStockRepository
import sg.com.quantai.middleware.requests.NewStockRequest

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
class NewStockControllerTest 
@Autowired 
constructor(
    private val newStockRepository: AssetStockRepository,
    private val restTemplate: TestRestTemplate
) {
    @LocalServerPort protected var port: Int = 0

    @BeforeEach
    fun setUp() {
        newStockRepository.deleteAll()
    }

    private fun getRootUrl(): String? = "http://localhost:$port/newStock"

    private fun prepareNewStockRequest(
        name: String = "Apple",
        symbol: String = "APPL",
        quantity: BigDecimal = BigDecimal(1),
        purchasePrice: BigDecimal= BigDecimal(20000),
    ) =
        NewStockRequest(
            name= name,
            symbol= symbol,
            quantity = quantity,
            purchasePrice = purchasePrice,
        )
    
    private fun saveOneStock(
        name: String = "Test Stock",
        symbol: String = "TEST",
        quantity: BigDecimal = BigDecimal(2),
        purchasePrice: BigDecimal = BigDecimal(10),
)=  
    newStockRepository.save(
        NewStock(
            name = name,
            symbol = symbol,
            quantity = quantity,
            purchasePrice = purchasePrice,
        )
    )

    // Create Functions
    @Test
    fun `should add stock`() {
        val stockRequest = prepareNewStockRequest()

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(stockRequest, HttpHeaders()),
                NewStock::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body?.uid)
        assertEquals(stockRequest.name, response.body?.name)
        assertEquals(stockRequest.symbol, response.body?.symbol)
        assertEquals(stockRequest.quantity, response.body?.quantity)
        assertEquals(stockRequest.purchasePrice, response.body?.purchasePrice)
    }

    @Test
    fun `should accept Empty Name`() {
        val stockRequest = prepareNewStockRequest(name="")

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(stockRequest, HttpHeaders()),
                NewStock::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body?.uid)
        assertEquals(stockRequest.name, response.body?.name)
        assertEquals(stockRequest.symbol, response.body?.symbol)
        assertEquals(stockRequest.quantity, response.body?.quantity)
        assertEquals(stockRequest.purchasePrice, response.body?.purchasePrice)
    }

    @Test
    fun `should accept Empty Symbol`() {
        val stockRequest = prepareNewStockRequest(symbol="")

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(stockRequest, HttpHeaders()),
                NewStock::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body?.uid)
        assertEquals(stockRequest.name, response.body?.name)
        assertEquals(stockRequest.symbol, response.body?.symbol)
        assertEquals(stockRequest.quantity, response.body?.quantity)
        assertEquals(stockRequest.purchasePrice, response.body?.purchasePrice)
    }

    @Test
    fun `should reject bad request - Empty Name and Symbol`() {
        val stockRequest = prepareNewStockRequest(name="",symbol="")

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(stockRequest, HttpHeaders()),
                NewStock::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - quantity negative`() {
        val stockRequest = prepareNewStockRequest(quantity = BigDecimal(-0.5))

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(stockRequest, HttpHeaders()),
                NewStock::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - purchase price 0`() {
        val stockRequest = prepareNewStockRequest(purchasePrice = BigDecimal(0))
        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(stockRequest, HttpHeaders()),
                NewStock::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - purchase price negative`() {
        val stockRequest = prepareNewStockRequest(purchasePrice = BigDecimal(-0.5))

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(stockRequest, HttpHeaders()),
                NewStock::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }
    
    // Read Functions
    @Test
    fun `should retrieve all stocks`() {
        saveOneStock()
        saveOneStock(name = "Test2")
        saveOneStock(name = "Test3")
        var response = restTemplate.getForEntity(getRootUrl(), List::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(3, response.body?.size)
        val names = response.body?.map { (it as Map<*, *>)["name"] } ?: emptyList()
        assertTrue(names.containsAll(listOf("Test NewStock", "Test2", "Test3")))
    }

    @Test
    fun `should get a single stock by uid`() {
        saveOneStock()
        saveOneStock(name = "Test2")
        val savedId = saveOneStock(name = "Test3").uid
        var response = restTemplate.getForEntity(getRootUrl()+"/$savedId", NewStock::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("Test3", response.body?.name)
    }

    @Test
    fun `should get a stock by name`() {
        saveOneStock(symbol = "Test1", name = "NewStock A")
        saveOneStock(symbol = "Test2", name = "NewStock A")
        saveOneStock(symbol = "Test3", name = "NewStock B")

        val name = "NewStock A"
        val response = restTemplate.getForEntity(getRootUrl() + "/name/$name", List::class.java)

        assertEquals(200, response.statusCode.value())

        val symbols = response.body?.map { (it as Map<*, *>)["symbol"] } ?: emptyList()
        assertTrue(symbols.containsAll(listOf("Test1","Test2")) && symbols.size == 2)
    }

    @Test
    fun `should get a stock by symbol`() {
        saveOneStock(symbol = "Test2", name = "NewStock A")
        saveOneStock(symbol = "Test2", name = "NewStock B")
        saveOneStock(symbol = "Test3", name = "NewStock C")

        val symbol = "Test2"
        val response = restTemplate.getForEntity(getRootUrl() + "/symbol/$symbol", List::class.java)

        assertEquals(200, response.statusCode.value())

        val names = response.body?.map { (it as Map<*, *>)["name"] } ?: emptyList()
        assertTrue(names.containsAll(listOf("NewStock A", "NewStock B")) && names.size == 2)
    }

    @Test
    fun `should get quantity of a stock by name`() {
        saveOneStock(symbol = "Test1", name = "NewStock A",quantity=BigDecimal(5))
        saveOneStock(symbol = "Test2", name = "NewStock A",quantity=BigDecimal(5))
        saveOneStock(symbol = "Test3", name = "NewStock B")

        val name = "NewStock A"
        val response = restTemplate.getForEntity(getRootUrl() + "/quantity/$name", BigDecimal::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(BigDecimal(10), response.body)
    }

    @Test
    fun `should get quantity of a stock by symbol`() {
        saveOneStock(symbol = "Test2", name = "NewStock A",quantity=BigDecimal(5))
        saveOneStock(symbol = "Test2", name = "NewStock B",quantity=BigDecimal(5))
        saveOneStock(symbol = "Test3", name = "NewStock C")

        val symbol = "Test2"
        val response = restTemplate.getForEntity(getRootUrl() + "/quantity/$symbol", BigDecimal::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(BigDecimal(10), response.body)
    }

    // delete

    @Test
    fun `should delete stock`() {
        val savedId = saveOneStock().uid

        val response = restTemplate.exchange(
            getRootUrl() + "/delete/$savedId",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )

        assertEquals("Deleted stock $savedId", response.body)
    }
}