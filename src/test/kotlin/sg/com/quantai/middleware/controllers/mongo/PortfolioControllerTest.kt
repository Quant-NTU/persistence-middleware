
import sg.com.quantai.middleware.repositories.mongo.*
import sg.com.quantai.middleware.data.mongo.*
import sg.com.quantai.middleware.requests.PortfolioRequest
import sg.com.quantai.middleware.data.mongo.enums.PortfolioActionEnum
import sg.com.quantai.middleware.controllers.mongo.PortfolioController

import org.mockito.Mockito.*
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
import java.math.BigDecimal
import sg.com.quantai.middleware.MiddlewareApplication


@SpringBootTest(classes = [MiddlewareApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension::class)

class PortfolioControllerTest 
@Autowired 
constructor(
    private val restTemplate: TestRestTemplate,
    private val portfolioRepository: PortfolioRepository,
    private val portfolioHistoryRepository: PortfolioHistoryRepository,
    private val cryptoRepository: CryptoRepository,
    private val stockRepository: StockRepository,
    private val forexRepository: ForexRepository,
    private val userRepository: UserRepository
) {
    @LocalServerPort protected var port: Int = 0

    @BeforeEach
    fun setUp() {
        portfolioRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun getRootUrl(): String? = "http://localhost:$port/portfolios"
    
    private fun saveOnePortfolio(
        name: String = "Test",
        description: String = "Test2",
        main: Boolean = false,
        owner: User = userRepository.save(User(name="Name", email="Email", password="Password", salt="Salt")),
    )=  
        portfolioRepository.save(
            Portfolio(   
                name = name,
                description = description,
                main = main,
                owner = owner,
            )
        )

        private fun saveOneAsset(
            name: String = "TestAsset",
            quantity: BigDecimal = BigDecimal(2),
            purchasePrice: BigDecimal = BigDecimal(3),
            symbol: String = "TST",
            portfolio: Portfolio = saveOnePortfolio()
        ) {
            val crypto = cryptoRepository.save(
                Crypto(
                    name = name,
                    quantity = quantity,
                    purchasePrice = purchasePrice,
                    symbol = symbol
                )
            )
        
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = crypto,
                    action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                    quantity = quantity,
                    value = quantity * purchasePrice,
                    portfolio = portfolio
                )
            )
        }
        

    private fun hashAndSaltPassword(plainTextPassword: String, salt: String? = null): Pair<String, String> {

        val generatedSalt = salt ?: BCrypt.gensalt()
        val hashedPassword = BCrypt.hashpw(plainTextPassword, generatedSalt)

        return Pair(hashedPassword, generatedSalt)
    }

    @Test
    fun `should return all portfolios from a user`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val (password2, salt2) = hashAndSaltPassword("Password2")
        val user1 = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val user2 = userRepository.save(User(name="Name2", email="Email2", password=password2, salt=salt2))
        val user1Id = user1.uid
        val user2Id = user2.uid

       // 1 (Default) portfolio for user 1
       var response =
        restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)

        // 1 (Default) portfolio for user 2
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)

        // 2 portfolio for user 1
        saveOnePortfolio(owner = user1)
        
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
        
        response =
            restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)

        saveOnePortfolio(owner = user1)
    }

    @Test
    fun `should not be able to delete main portfolio, but can delete others`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val user1 = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val user1Id = user1.uid

        val portfolio1 = saveOnePortfolio(owner = user1,main=true)
        val portfolio1_Id = portfolio1.uid
        val portfolio2 = saveOnePortfolio(owner = user1)
        val portfolio2_Id = portfolio2.uid

        saveOneAsset(portfolio = portfolio1)
        saveOneAsset(portfolio = portfolio2)

        var response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(2, response.body?.size)

        val deleteResponse1 = restTemplate.exchange(
            getRootUrl() + "/user/$user1Id/$portfolio1_Id",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )

        assertEquals("Cannot delete main portfolio.", deleteResponse1.body)
    
        response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(2, response.body?.size)
    
        val deleteResponse3 = restTemplate.exchange(
            getRootUrl() + "/user/$user1Id/$portfolio2_Id",
            HttpMethod.DELETE,
            HttpEntity(null, HttpHeaders()),
            String::class.java
        )
        assertEquals("Deleted portfolio ${portfolio2.name}", deleteResponse3.body)
    
        response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(1, response.body?.size)

        val portfoliohistory: List<PortfolioHistory> = portfolioHistoryRepository.findByPortfolio(portfolio1)
        assertEquals(2, portfoliohistory.size)
    }
}

class PortfolioControllerTest2 
@Autowired 
constructor(
    private val restTemplate: TestRestTemplate,
    private val portfolioRepository: PortfolioRepository,
    private val portfolioHistoryRepository: PortfolioHistoryRepository,
    private val cryptoRepository: CryptoRepository,
    private val stockRepository: StockRepository,
    private val forexRepository: ForexRepository,
    private val userRepository: UserRepository
) {
    @LocalServerPort protected var port: Int = 0

    @BeforeEach
    fun setUp() {
        portfolioRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun getRootUrl(): String? = "http://localhost:$port/portfolios"
    
    private fun saveOnePortfolio(
        name: String = "Test",
        description: String = "Test2",
        main: Boolean = false,
        owner: User = userRepository.save(User(name="Name", email="Email", password="Password", salt="Salt")),
    )=  
        portfolioRepository.save(
            Portfolio(   
                name = name,
                description = description,
                main = main,
                owner = owner,
            )
        )

        private fun saveOneCrypto(
            name: String = "TestCrypto",
            quantity: BigDecimal = BigDecimal(2),
            purchasePrice: BigDecimal = BigDecimal(3),
            symbol: String = "TSTCR",
            portfolio: Portfolio = saveOnePortfolio()
        ) {
            val crypto = cryptoRepository.save(
                Crypto(
                    name = name,
                    quantity = quantity,
                    purchasePrice = purchasePrice,
                    symbol = symbol
                )
            )
        
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = crypto,
                    action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                    quantity = quantity,
                    value = quantity * purchasePrice,
                    portfolio = portfolio
                )
            )
        }

        private fun saveOneStock(
            name: String = "TestStock",
            quantity: BigDecimal = BigDecimal(2),
            purchasePrice: BigDecimal = BigDecimal(3),
            symbol: String = "TSTST",
            portfolio: Portfolio = saveOnePortfolio()
        ) {
            val Stock = stockRepository.save(
                Stock(
                    name = name,
                    quantity = quantity,
                    purchasePrice = purchasePrice,
                    symbol = symbol
                )
            )
        
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = stock,
                    action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                    quantity = quantity,
                    value = quantity * purchasePrice,
                    portfolio = portfolio
                )
            )
        }

        private fun saveOneForex(
            name: String = "TestForex",
            quantity: BigDecimal = BigDecimal(2),
            purchasePrice: BigDecimal = BigDecimal(3),
            symbol: String = "TSTFR",
            portfolio: Portfolio = saveOnePortfolio()
        ) {
            val Forex = forexRepository.save(
                Forex(
                    name = name,
                    quantity = quantity,
                    purchasePrice = purchasePrice,
                    symbol = symbol
                )
            )
        
            portfolioHistoryRepository.save(
                PortfolioHistory(
                    asset = forex,
                    action = PortfolioActionEnum.ADD_MANUAL_ASSET,
                    quantity = quantity,
                    value = quantity * purchasePrice,
                    portfolio = portfolio
                )
            )
        }
        

    private fun hashAndSaltPassword(plainTextPassword: String, salt: String? = null): Pair<String, String> {

        val generatedSalt = salt ?: BCrypt.gensalt()
        val hashedPassword = BCrypt.hashpw(plainTextPassword, generatedSalt)

        return Pair(hashedPassword, generatedSalt)
    }

    @Test
    fun `should delete crypto asset from portfolio`() {
        val (passwordCR, saltCR) = hashAndSaltPassword("PasswordCR")
        val userCR = userRepository.save(User(name="NameCR", email="EmailCR", password=passwordCR, salt=saltCR))
        val userCRId = userCR.uid

        val portfolioCR = saveOnePortfolio(owner=userCR, main=true)
        val portfolioCR_Id = portfolioCR.uid
        val saveOneCrypto(portfolio = portfolioCR)

        // Ensure the Crypto asset exists
        assertNotNull(cryptoRepository.findByName("TSTCR"))

        val deleteRequest = DeleteCryptoRequest(name = "TSTCR", portfolio_uid = portfolioCR.uid, deleteAll = true)
        val requestJson = ObjectMapper().writeValueAsString(deleteRequest)

        val result = mockMvc.perform(
            MockMvcRequestBuilders.delete("/asset/crypto/${portfolio.uid}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()

        val deletedCrypto = cryptoRepository.findByName("TSTCR")
        assertNull(deletedCrypto)
    }

    @Test
    fun `should delete stock asset from portfolio`() {
        val (passwordST, saltST) = hashAndSaltPassword("PasswordST")
        val userST = userRepository.save(User(name="NameST", email="EmailST", password=passwordST, salt=saltST))
        val userSTId = userST.uid

        val portfolioST = saveOnePortfolio(owner=userST, main=true)
        val portfolioST_Id = portfolioST.uid
        val saveOneStock(portfolio = portfolioST)

        assertNotNull(stockRepository.findByName("TSTST"))

        val deleteRequest = DeleteStockRequest(name = "TSTST", portfolio_uid = portfolioST.uid, deleteAll = true)
        val requestJson = ObjectMapper().writeValueAsString(deleteRequest)

        val result = mockMvc.perform(
            MockMvcRequestBuilders.delete("/asset/stock/${portfolio.uid}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()

        val deletedStock = stockRepository.findByName("TSTST")
        assertNull(deletedStock)
    }

    @Test
    fun `should delete Forex asset from portfolio`() {
        val (passwordFR, saltFR) = hashAndSaltPassword("PasswordFR")
        val userFR = userRepository.save(User(name="NameFR", email="EmailFR", password=passwordFR, salt=saltFR))
        val userFRId = userFR.uid

        val portfolioFR = saveOnePortfolio(owner=userFR, main=true)
        val portfolioFR_Id = portfolioFR.uid
        val saveOneForex(portfolio = portfolioFR)

        assertNotNull(forexRepository.findByName("TSTFR"))

        val deleteRequest = DeleteForexRequest(name = "TSTFR", portfolio_uid = portfolioFR.uid, deleteAll = true)
        val requestJson = ObjectMapper().writeValueAsString(deleteRequest)

        val result = mockMvc.perform(
            MockMvcRequestBuilders.delete("/asset/forex/${portfolio.uid}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()

        val deletedForex = stockRepository.findByName("TSTFR")
        assertNull(deletedForex)
    }
}