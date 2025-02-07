package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.NewCrypto
import sg.com.quantai.middleware.repositories.AssetCryptoRepository
import sg.com.quantai.middleware.requests.NewCryptoRequest

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
class NewCryptoControllerTest 
@Autowired 
constructor(
    private val newCryptoRepository: AssetCryptoRepository,
    private val restTemplate: TestRestTemplate
) {
    @LocalServerPort protected var port: Int = 0

    @BeforeEach
    fun setUp() {
        newCryptoRepository.deleteAll()
    }

    private fun getRootUrl(): String? = "http://localhost:$port/new-crypto"

    private fun prepareNewCryptoRequest(
        name: String = "Bitcoin",
        symbol: String = "BTC",
        quantity: BigDecimal = BigDecimal(1),
        purchasePrice: BigDecimal= BigDecimal(20000),
    ) =
        NewCryptoRequest(
            name= name,
            symbol= symbol,
            quantity = quantity,
            purchasePrice = purchasePrice,
        )
    
    private fun saveOneCrypto(
        name: String = "Test Crypto",
        symbol: String = "TEST",
        quantity: BigDecimal = BigDecimal(2),
        purchasePrice: BigDecimal = BigDecimal(10),
)=  
    newCryptoRepository.save(
        NewCrypto(
            name = name,
            symbol = symbol,
            quantity = quantity,
            purchasePrice = purchasePrice,
        )
    )

    // Create Functions
    @Test
    fun `should add crypto`() {
        val cryptoRequest = prepareNewCryptoRequest()

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body?.uid)
        assertEquals(cryptoRequest.name, response.body?.name)
        assertEquals(cryptoRequest.symbol, response.body?.symbol)
        assertEquals(cryptoRequest.quantity, response.body?.quantity)
        assertEquals(cryptoRequest.purchasePrice, response.body?.purchasePrice)
    }

    @Test
    fun `should accept Empty Name`() {
        val cryptoRequest = prepareNewCryptoRequest(name="")

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body?.uid)
        assertEquals(cryptoRequest.name, response.body?.name)
        assertEquals(cryptoRequest.symbol, response.body?.symbol)
        assertEquals(cryptoRequest.quantity, response.body?.quantity)
        assertEquals(cryptoRequest.purchasePrice, response.body?.purchasePrice)
    }

    @Test
    fun `should accept Empty Symbol`() {
        val cryptoRequest = prepareNewCryptoRequest(symbol="")

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body?.uid)
        assertEquals(cryptoRequest.name, response.body?.name)
        assertEquals(cryptoRequest.symbol, response.body?.symbol)
        assertEquals(cryptoRequest.quantity, response.body?.quantity)
        assertEquals(cryptoRequest.purchasePrice, response.body?.purchasePrice)
    }

    @Test
    fun `should reject bad request - Empty Name and Symbol`() {
        val cryptoRequest = prepareNewCryptoRequest(name="",symbol="")

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - quantity negative`() {
        val cryptoRequest = prepareNewCryptoRequest(quantity = BigDecimal(-0.5))

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - purchase price 0`() {
        val cryptoRequest = prepareNewCryptoRequest(purchasePrice = BigDecimal(0))
        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - purchase price negative`() {
        val cryptoRequest = prepareNewCryptoRequest(purchasePrice = BigDecimal(-0.5))

        val response =
            restTemplate.exchange(
                getRootUrl() + "/add",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }
    
    // Read Functions
    @Test
    fun `should retrieve all cryptos`() {
        saveOneCrypto()
        saveOneCrypto(name = "Test2")
        saveOneCrypto(name = "Test3")
        var response = restTemplate.getForEntity(getRootUrl(), List::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(3, response.body?.size)
        val names = response.body?.map { (it as Map<*, *>)["name"] } ?: emptyList()
        assertTrue(names.containsAll(listOf("Test NewCrypto", "Test2", "Test3")))
    }

    @Test
    fun `should get a single crypto by uid`() {
        saveOneCrypto()
        saveOneCrypto(name = "Test2")
        val savedId = saveOneCrypto(name = "Test3").uid
        var response = restTemplate.getForEntity(getRootUrl()+"/$savedId", NewCrypto::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("Test3", response.body?.name)
    }

    @Test
    fun `should get a crypto by name`() {
        saveOneCrypto(symbol = "Test1", name = "NewCrypto A")
        saveOneCrypto(symbol = "Test2", name = "NewCrypto A")
        saveOneCrypto(symbol = "Test3", name = "NewCrypto B")

        val name = "NewCrypto A"
        val response = restTemplate.getForEntity(getRootUrl() + "/name/$name", List::class.java)

        assertEquals(200, response.statusCode.value())

        val symbols = response.body?.map { (it as Map<*, *>)["symbol"] } ?: emptyList()
        assertTrue(symbols.containsAll(listOf("Test1","Test2")) && symbols.size == 2)
    }

    @Test
    fun `should get a crypto by symbol`() {
        saveOneCrypto(symbol = "Test2", name = "NewCrypto A")
        saveOneCrypto(symbol = "Test2", name = "NewCrypto B")
        saveOneCrypto(symbol = "Test3", name = "NewCrypto C")

        val symbol = "Test2"
        val response = restTemplate.getForEntity(getRootUrl() + "/symbol/$symbol", List::class.java)

        assertEquals(200, response.statusCode.value())

        val names = response.body?.map { (it as Map<*, *>)["name"] } ?: emptyList()
        assertTrue(names.containsAll(listOf("NewCrypto A", "NewCrypto B")) && names.size == 2)
    }

    @Test
    fun `should get quantity of a crypto by name`() {
        saveOneCrypto(symbol = "Test1", name = "NewCrypto A",quantity=BigDecimal(5))
        saveOneCrypto(symbol = "Test2", name = "NewCrypto A",quantity=BigDecimal(5))
        saveOneCrypto(symbol = "Test3", name = "NewCrypto B")

        val name = "NewCrypto A"
        val response = restTemplate.getForEntity(getRootUrl() + "/quantity/$name", BigDecimal::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(BigDecimal(10), response.body)
    }

    @Test
    fun `should get quantity of a crypto by symbol`() {
        saveOneCrypto(symbol = "Test2", name = "NewCrypto A",quantity=BigDecimal(5))
        saveOneCrypto(symbol = "Test2", name = "NewCrypto B",quantity=BigDecimal(5))
        saveOneCrypto(symbol = "Test3", name = "NewCrypto C")

        val symbol = "Test2"
        val response = restTemplate.getForEntity(getRootUrl() + "/quantity/$symbol", BigDecimal::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals(BigDecimal(10), response.body)
    }

    // delete

    @Test
    fun `should delete crypto`() {
        val savedId = saveOneCrypto().uid

        val response = restTemplate.exchange(
            getRootUrl() + "/delete/$savedId",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )

        assertEquals("Deleted crypto $savedId", response.body)
    }
}