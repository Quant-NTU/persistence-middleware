package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.NewCrypto
import sg.com.quantai.middleware.repositories.AssetCryptoRepository
import sg.com.quantai.middleware.requests.NewCryptoRequest
import sg.com.quantai.middleware.requests.CryptoRequest

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

    private fun getRootUrl(): String? = "http://localhost:$port/new-cryptos"

    private fun prepareCryptoRequest(
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
    fun `should create crypto`() {
        val cryptoRequest = prepareCryptoRequest()

        val response =
            restTemplate.exchange(
                getRootUrl() + "/create",
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
    fun `should reject bad request - Empty Name`() {
        val cryptoRequest = prepareCryptoRequest(name="")

        val response =
            restTemplate.exchange(
                getRootUrl() + "/create",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - Empty Symbol`() {
        val cryptoRequest = prepareCryptoRequest(symbol="")

        val response =
            restTemplate.exchange(
                getRootUrl() + "/create",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - quantity 0`() {
        val cryptoRequest = prepareCryptoRequest(quantity = BigDecimal(0))
        val response =
            restTemplate.exchange(
                getRootUrl() + "/create",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - quantity negative`() {
        val cryptoRequest = prepareCryptoRequest(quantity = BigDecimal(-0.5))

        val response =
            restTemplate.exchange(
                getRootUrl() + "/create",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - purchase price 0`() {
        val cryptoRequest = prepareCryptoRequest(purchasePrice = BigDecimal(0))
        val response =
            restTemplate.exchange(
                getRootUrl() + "/create",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `should reject bad request - purchase price negative`() {
        val cryptoRequest = prepareCryptoRequest(purchasePrice = BigDecimal(-0.5))

        val response =
            restTemplate.exchange(
                getRootUrl() + "/create",
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
        assertTrue(names.containsAll(listOf("Test Crypto", "Test2", "Test3")))
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
    fun `should get a single crypto by name`() {
        saveOneCrypto()
        saveOneCrypto(name = "Test2")
        saveOneCrypto(name = "Test3")
        val Name = "Test3"
        var response = restTemplate.getForEntity(getRootUrl()+"/name/$Name", NewCrypto::class.java)

        assertEquals(200, response.statusCode.value())
        assertEquals("Test3", response.body?.name)
    }

    @Test
    fun `should get multiple cryptos by providing list of symbols`() {
        saveOneCrypto()
        saveOneCrypto(name = "Test2",symbol = "TT")
        saveOneCrypto(name = "Test3",symbol = "TTT")
        val symbolsString = "TEST,TT"
        val symbolsList: List<String> = symbolsString.split(",").map { it.trim() }

        val cryptoRequest = CryptoRequest(
            symbols = symbolsList
        )
        val response =
            restTemplate.exchange(
                getRootUrl() + "/symbols",
                HttpMethod.POST,
                HttpEntity(cryptoRequest, HttpHeaders()),
                List::class.java
            )

        assertEquals(200, response.statusCode.value())
        assertEquals(2, response.body?.size)

        val symbols = response.body?.map { (it as Map<*, *>)["symbol"] } ?: emptyList()
        assertTrue(symbols.containsAll(listOf("TEST", "TT")))
    }

    // update functions
    @Test
    fun `should update crypto`() {
        val savedId = saveOneCrypto().uid
        val cryptoRequest = prepareCryptoRequest()

        val response =
            restTemplate.exchange(
                getRootUrl() + "/$savedId",
                HttpMethod.PUT,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(200, response.statusCode.value())
        assertEquals(cryptoRequest.name, response.body?.name)
        assertEquals(cryptoRequest.symbol, response.body?.symbol)
        assertEquals(cryptoRequest.quantity, response.body?.quantity)
        assertEquals(cryptoRequest.purchasePrice, response.body?.purchasePrice)
    }

    @Test
    fun `update - should reject bad request - Empty Name`() {
        val savedId = saveOneCrypto().uid
        val cryptoRequest = prepareCryptoRequest(name="")
        val response =
            restTemplate.exchange(
                getRootUrl() + "/$savedId",
                HttpMethod.PUT,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `update - should reject bad request - Empty Symbol`() {
        val cryptoRequest = prepareCryptoRequest(symbol="")
        val savedId = saveOneCrypto().uid
        val response =
            restTemplate.exchange(
                getRootUrl() + "/$savedId",
                HttpMethod.PUT,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `update - should reject bad request - quantity 0`() {
        val cryptoRequest = prepareCryptoRequest(quantity = BigDecimal(0))
        val savedId = saveOneCrypto().uid
        val response =
            restTemplate.exchange(
                getRootUrl() + "/$savedId",
                HttpMethod.PUT,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `update - should reject bad request - quantity negative`() {
        val cryptoRequest = prepareCryptoRequest(quantity = BigDecimal(-0.5))
        val savedId = saveOneCrypto().uid
        val response =
            restTemplate.exchange(
                getRootUrl() + "/$savedId",
                HttpMethod.PUT,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `update- should reject bad request - purchase price 0`() {
        val cryptoRequest = prepareCryptoRequest(purchasePrice = BigDecimal(0))
        val savedId = saveOneCrypto().uid
        val response =
            restTemplate.exchange(
                getRootUrl() + "/$savedId",
                HttpMethod.PUT,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    @Test
    fun `update- should reject bad request - purchase price negative`() {
        val cryptoRequest = prepareCryptoRequest(purchasePrice = BigDecimal(-0.5))
        val savedId = saveOneCrypto().uid
        val response =
            restTemplate.exchange(
                getRootUrl() + "/$savedId",
                HttpMethod.PUT,
                HttpEntity(cryptoRequest, HttpHeaders()),
                NewCrypto::class.java
            )

        assertEquals(400 , response.statusCode.value())
    }

    // delete function
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