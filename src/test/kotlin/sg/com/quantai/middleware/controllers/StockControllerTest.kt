package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.*
import sg.com.quantai.middleware.requests.StockListRequest
import sg.com.quantai.middleware.requests.StockRequest
import sg.com.quantai.middleware.repositories.StockRepository
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockControllerTest
@Autowired
constructor(
        private val stockRepository: StockRepository,
        private val restTemplate: TestRestTemplate
) {

    @LocalServerPort protected var port: Int = 0

    @BeforeEach
    fun setUp() {
        stockRepository.deleteAll()
    }

    private fun getRootUrl(): String? = "http://localhost:$port/stock"

    private fun saveOneStock() =
        stockRepository.save(
            Stock(
                symbol="STOCK1",
                name="Stock 1",
                marketCap=BigDecimal(1),
                price=BigDecimal(1.1),
                change=BigDecimal(1.1),
                volume="1"
            )
        )

    private fun saveAnotherStock() =
        stockRepository.save(
            Stock(
                symbol="STOCK2",
                name="Stock 2",
                marketCap=BigDecimal(2),
                price=BigDecimal(2.2),
                change=BigDecimal(2.2),
                volume="2"
            )
        )

    private fun prepareStockListRequest() =
        StockListRequest(symbols=listOf("STOCK1"))

    private fun prepare2StockListRequest() =
        StockListRequest(symbols=listOf("STOCK1", "STOCK2"))

    private fun prepare3StockListRequest() =
        StockListRequest(symbols=listOf("STOCK1", "STOCK2", "STOCK3"))

    private fun prepareStockRequest() =
        StockRequest(
            symbol="STOCK1",
            name="Stock 1",
            marketCap=BigDecimal(1),
            price=BigDecimal(1.1),
            change=BigDecimal(1.1),
            volume="1"
        )

    @Test
    fun `should return all stocks`() {
        // No Stocks
        var response =
                restTemplate.getForEntity(getRootUrl() + "", Array<Stock>::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOneStock()
        // 1 Stock
        response =
                restTemplate.getForEntity(getRootUrl() + "", Array<Stock>::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)
        assertEquals("STOCK1", response.body?.get(0)?.symbol)

        saveAnotherStock()
        // 2 Stocks
        response =
            restTemplate.getForEntity(getRootUrl() + "", Array<Stock>::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
        assertEquals("STOCK1", response.body?.get(0)?.symbol)
        assertEquals("STOCK2", response.body?.get(1)?.symbol)
    }

    @Test
    fun `should return single stock by id`() {
        val savedId = saveOneStock().uuid

        val response =
            restTemplate.getForEntity(getRootUrl() + "/$savedId", Stock::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(savedId, response.body?.uuid)
        assertEquals("STOCK1", response.body?.symbol)
    }

    @Test
    fun `should return single stock by symbol`() {
        //No Stock
        var response =
            restTemplate.getForEntity(getRootUrl() + "/symbol/STOCK2", Stock::class.java)

        assertEquals(200, response.statusCode.value())
        assertNull(response.body)

        //1 Stock, but different symbol
        saveOneStock()
        response =
            restTemplate.getForEntity(getRootUrl() + "/symbol/STOCK2", Stock::class.java)

        assertEquals(200, response.statusCode.value())
        assertNull(response.body)

        val symbol = saveAnotherStock().symbol

        response =
            restTemplate.getForEntity(getRootUrl() + "/symbol/$symbol", Stock::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("STOCK2", response.body?.symbol)
    }

    @Test
    fun `should return all stock with symbol within list of symbols`() {
        //No Stock
        var stockListRequest = prepareStockListRequest()

        var response =
            restTemplate.postForEntity(getRootUrl() + "/symbol", stockListRequest, Array<Stock>::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        //1 Stock
        saveOneStock()
        response =
            restTemplate.postForEntity(getRootUrl() + "/symbol", stockListRequest, Array<Stock>::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)
        assertEquals("STOCK1", response.body?.get(0)?.symbol)

        //2 Stocks
        saveAnotherStock()
        stockListRequest = prepare2StockListRequest()

        response =
            restTemplate.postForEntity(getRootUrl() + "/symbol", stockListRequest, Array<Stock>::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
        assertEquals("STOCK1", response.body?.get(0)?.symbol)
        assertEquals("STOCK2", response.body?.get(1)?.symbol)

        //2 Stocks but query asks for 3
        stockListRequest = prepare3StockListRequest()

        response =
            restTemplate.postForEntity(getRootUrl() + "/symbol", stockListRequest, Array<Stock>::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
        assertEquals("STOCK1", response.body?.get(0)?.symbol)
        assertEquals("STOCK2", response.body?.get(1)?.symbol)
    }

    @Test
    fun `should create a new Portfolio asset or update it`() {
        var stockRequest = prepareStockRequest()

        var response =
            restTemplate.postForEntity(
                getRootUrl() + "/add",
                stockRequest,
                Stock::class.java
            )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("STOCK1", response.body?.symbol)
    }
}
