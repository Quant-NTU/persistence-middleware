package sg.com.quantai.middleware.controllers

import sg.com.quantai.middleware.data.Transaction
import sg.com.quantai.middleware.data.User
import sg.com.quantai.middleware.data.Crypto
import sg.com.quantai.middleware.repositories.TransactionRepository
import sg.com.quantai.middleware.repositories.UserRepository
import sg.com.quantai.middleware.repositories.CryptoRepository
import sg.com.quantai.middleware.requests.TransactionRequest
import sg.com.quantai.middleware.data.enums.TransactionStatus
import sg.com.quantai.middleware.data.enums.TransactionType
import sg.com.quantai.middleware.services.POJOS.CoinDetails.LinkObj
import sg.com.quantai.middleware.services.POJOS.CoinHistory.*
import sg.com.quantai.middleware.services.POJOS.CoinHistory.CoinHistoryInstance
import sg.com.quantai.middleware.services.POJOS.CoinHistory.HistoryInstance
import sg.com.quantai.middleware.services.POJOS.TopCoin.*

import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.mindrot.jbcrypt.BCrypt

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionControllerTest
@Autowired
constructor(
        private val transactionRepository: TransactionRepository,
        private val userRepository: UserRepository,
        private val cryptoRepository: CryptoRepository,
        private val restTemplate: TestRestTemplate
) {

    @LocalServerPort protected var port: Int = 0

    @BeforeEach
    fun setUp() {
        transactionRepository.deleteAll()
        userRepository.deleteAll()
        cryptoRepository.deleteAll()
    }

    private fun getRootUrl(): String? = "http://localhost:$port/transactions"

    val coinHistory: List<HistoryInstance> =
            listOf(
                    HistoryInstance(BigDecimal(98.0), 23042023),
                    HistoryInstance(BigDecimal(99.0), 24042023)
            )

    val coinHistoryInstance = CoinHistoryInstance(BigDecimal(1.0), coinHistory)
    val linksObj : List<LinkObj> = 
            listOf(
                    LinkObj("Link 1", "This is link 1", "link"), 
                    LinkObj("Link 2", "This is link 2", "link")
            )

    private fun saveOneTransaction(
            crypto: Crypto =
                    cryptoRepository.save(
                            Crypto(
                                    "Bitcoin",
                                    "BTC",
                                    BigDecimal(100.0),
                                    BigDecimal(1.0),
                                    LocalDateTime.now(),
                                    "This is a coin",
                                    "This is a url",
                                    BigDecimal(5.0),
                                    1,
                                    "23456",
                                    BigDecimal(50.0),
                                    100,
                                    10,
                                    true,
                                    "1000000",
                                    "500000",
                                    linksObj,
                                    coinHistoryInstance,
                                    uuid = "123",
                                    )
                    ),
            owner: User = userRepository.save(User(name="Name", email="Email", password="Password", salt="Salt"))
    ) =
            transactionRepository.save(
                    Transaction(
                            crypto = crypto,
                            stock = null,
                            quantity = BigDecimal(1.0),
                            price = BigDecimal(100.0),
                            type = TransactionType.BUY,
                            strategy = null,
                            strategyId = null,
                            maxBuyPrice = null,
                            minSellPrice = null,
                            owner = owner
                    )
            )

    private fun prepareTransactionRequest() =
            TransactionRequest(crypto=null,
                               stock=null,
                               quantity=BigDecimal(2.0),
                               price=BigDecimal(200.0),
                               maxBuyPrice=null,
                               minSellPrice=null,
                               type=TransactionType.SELL,
                               strategy=null,
                               strategyId=null,
                               status=TransactionStatus.PENDING)

    private fun hashAndSaltPassword(plainTextPassword: String, salt: String? = null): Pair<String, String> {

        val generatedSalt = salt ?: BCrypt.gensalt()
        val hashedPassword = BCrypt.hashpw(plainTextPassword, generatedSalt)

        return Pair(hashedPassword, generatedSalt)
    }

    @Test
    fun `should return all transactions from a user`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val (password2, salt2) = hashAndSaltPassword("Password2")
        val user1 = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val user2 = userRepository.save(User(name="Name2", email="Email2", password=password2, salt=salt2))
        val defaultUser1Id = user1.uid
        val defaultUser2Id = user2.uid

        // No transactions for user 1
        var response =
                restTemplate.getForEntity(getRootUrl() + "/user/$defaultUser1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        // No transactions for user 2
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$defaultUser2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOneTransaction(owner = user1)
        // 1 transaction for user 1
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$defaultUser1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)
        // No transactions for user 2
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$defaultUser2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOneTransaction(owner = user1)
        // 2 transactions for user 1
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$defaultUser1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
        // No transactions for user 2
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$defaultUser2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOneTransaction(owner = user2)
        // 2 transactions for user 1
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$defaultUser1Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
        // 1 transaction for user 2
        response =
                restTemplate.getForEntity(getRootUrl() + "/user/$defaultUser2Id", List::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)
    }

    @Test
    fun `should return all transactions from a crypto`() {
        val crypto1 =
                cryptoRepository.save(
                        Crypto(
                                "Bitcoin",
                                "BTC",
                                BigDecimal(200.0),
                                BigDecimal(1.0),
                                LocalDateTime.now(),
                                "This is a coin",
                                    "This is a url",
                                    BigDecimal(5.0),
                                    1,
                                    "23456",
                                    BigDecimal(50.0),
                                    100,
                                    10,
                                    true,
                                    "1000000",
                                    "500000",
                                    linksObj,
                                    coinHistoryInstance,
                                    uuid = "123",
                                )
                )
        val crypto2 =
                cryptoRepository.save(
                        Crypto(
                                "Litecoin",
                                "LTC",
                                BigDecimal(100.0),
                                BigDecimal(1.0),
                                LocalDateTime.now(),
                                "This is a coin",
                                    "This is a url",
                                    BigDecimal(5.0),
                                    1,
                                    "23456",
                                    BigDecimal(50.0),
                                    100,
                                    10,
                                    true,
                                    "1000000",
                                    "500000",
                                    linksObj,
                                    coinHistoryInstance,
                                    uuid = "456",
                                )
                )
        val defaultCrypto1Id = crypto1.uuid
        val defaultCrypto2Id = crypto2.uuid

        // No transactions for crypto 1
        var response =
                restTemplate.getForEntity(
                        getRootUrl() + "/crypto/$defaultCrypto1Id",
                        List::class.java
                )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        // No transactions for crypto 2
        response =
                restTemplate.getForEntity(
                        getRootUrl() + "/crypto/$defaultCrypto2Id",
                        List::class.java
                )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOneTransaction(crypto = crypto1)
        // 1 transaction for crypto 1
        response =
                restTemplate.getForEntity(
                        getRootUrl() + "/crypto/$defaultCrypto1Id",
                        List::class.java
                )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)
        // No transactions for crypto 2
        response =
                restTemplate.getForEntity(
                        getRootUrl() + "/crypto/$defaultCrypto2Id",
                        List::class.java
                )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOneTransaction(crypto = crypto1)
        // 2 transactions for crypto 1
        response =
                restTemplate.getForEntity(
                        getRootUrl() + "/crypto/$defaultCrypto1Id",
                        List::class.java
                )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
        // No transactions for crypto 2
        response =
                restTemplate.getForEntity(
                        getRootUrl() + "/crypto/$defaultCrypto2Id",
                        List::class.java
                )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(0, response.body?.size)

        saveOneTransaction(crypto = crypto2)
        // 2 transactions for crypto 1
        response =
                restTemplate.getForEntity(
                        getRootUrl() + "/crypto/$defaultCrypto1Id",
                        List::class.java
                )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(2, response.body?.size)
        // 1 transaction for crypto 2
        response =
                restTemplate.getForEntity(
                        getRootUrl() + "/crypto/$defaultCrypto2Id",
                        List::class.java
                )

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(1, response.body?.size)
    }

    @Test
    fun `should return single transaction by id`() {
        val crypto =
                cryptoRepository.save(
                        Crypto(
                                "Bitcoin",
                                "BTC",
                                BigDecimal(200.0),
                                BigDecimal(1.0),
                                LocalDateTime.now(),
                                "This is a coin",
                                    "This is a url",
                                    BigDecimal(5.0),
                                    1,
                                    "23456",
                                    BigDecimal(50.0),
                                    100,
                                    10,
                                    true,
                                    "1000000",
                                    "500000",
                                    linksObj,
                                    coinHistoryInstance,
                                    uuid = "123",
                                )
                )
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val user = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val savedId = saveOneTransaction(crypto, user).uid

        val response =
                restTemplate.getForEntity(getRootUrl() + "/$savedId", Transaction::class.java)

        assertEquals(200, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(crypto.uuid, response.body?.crypto?.uuid)
        assertEquals(user.uid, response.body?.owner?.uid)
    }

    @Test
    fun `should delete existing transaction`() {
        val savedId = saveOneTransaction().uid

        val delete =
                restTemplate.exchange(
                        getRootUrl() + "/$savedId",
                        HttpMethod.DELETE,
                        HttpEntity(null, HttpHeaders()),
                        ResponseEntity::class.java
                )

        assertEquals(204, delete.statusCode.value())
        assertThrows(EmptyResultDataAccessException::class.java) {
            transactionRepository.findOneByUid(savedId)
        }
    }

    @Test
    fun `should update existing transaction`() {
        val transaction = saveOneTransaction()
        val transactionId = transaction.uid
        val transactionRequest = prepareTransactionRequest()

        val updatedResponse =
                restTemplate.exchange(
                        getRootUrl() + "/$transactionId",
                        HttpMethod.PUT,
                        HttpEntity(transactionRequest, HttpHeaders()),
                        Transaction::class.java
                )
        val updatedTransaction = transactionRepository.findOneByUid(transactionId)

        assertEquals(200, updatedResponse.statusCode.value())
        assertEquals(transactionId, updatedTransaction.uid)
        assertEquals(
                transaction.owner.uid,
                updatedTransaction.owner.uid
        ) // Shouldn't allow change the transaction's owner
        assertEquals(
                transaction.crypto?.uuid,
                updatedTransaction.crypto?.uuid
        ) // Shouldn't allow change the transaction's crypto
        assertEquals(transactionRequest.quantity, updatedTransaction.quantity)
        assertEquals(transactionRequest.price, updatedTransaction.price)
        assertEquals(transactionRequest.type, updatedTransaction.type)
        assertEquals(transactionRequest.status, updatedTransaction.status)
        assertNotEquals(transaction.updatedDate, updatedTransaction.updatedDate)
    }

    @Test
    fun `should create new transaction add attach it to user`() {
        val (password, salt) = hashAndSaltPassword("Password")
        val user = userRepository.save(User(name="Name", email="Email", password=password, salt=salt))
        var userId = user.uid
        val crypto =
                cryptoRepository.save(
                        Crypto(
                                "Bitcoin",
                                "BTC",
                                BigDecimal(200.0),
                                BigDecimal(1.0),
                                LocalDateTime.now(),
                                "This is a coin",
                                "This is a url",
                                BigDecimal(5.0),
                                1,
                                "23456",
                                BigDecimal(50.0),
                                100,
                                10,
                                true,
                                "1000000",
                                "500000",
                                linksObj,
                                coinHistoryInstance,
                                uuid = "123",
                                )
                )
        val cryptoId = crypto.uuid
        val transactionRequest = prepareTransactionRequest()

        val response =
                restTemplate.postForEntity(
                        getRootUrl() + "/user/$userId/crypto/$cryptoId",
                        transactionRequest,
                        Transaction::class.java
                )

        assertEquals(201, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals(crypto.uuid, response.body?.crypto?.uuid)
        assertEquals(user.uid, response.body?.owner?.uid)
        assertEquals(transactionRequest.quantity, response.body?.quantity)
        assertEquals(transactionRequest.price, response.body?.price)
        assertEquals(transactionRequest.type, response.body?.type)
        assertEquals(transactionRequest.status, response.body?.status)
    }

    @Test
    fun `should delete all transactions from a user`() {
        val (password1, salt1) = hashAndSaltPassword("Password1")
        val (password2, salt2) = hashAndSaltPassword("Password2")
        val user1 = userRepository.save(User(name="Name1", email="Email1", password=password1, salt=salt1))
        val user2 = userRepository.save(User(name="Name2", email="Email2", password=password2, salt=salt2))
        val user1Id = user1.uid
        val user2Id = user2.uid

        saveOneTransaction(owner = user1)
        saveOneTransaction(owner = user1)
        saveOneTransaction(owner = user2)

        var response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(2, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)
        assertEquals(1, response.body?.size)

        // Delete transactions for user 1

        restTemplate.exchange(
                getRootUrl() + "/user/$user1Id",
                HttpMethod.DELETE,
                HttpEntity(null, HttpHeaders()),
                ResponseEntity::class.java
        )

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(0, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)
        assertEquals(1, response.body?.size)

        // Delete transactions for user 2

        restTemplate.exchange(
                getRootUrl() + "/user/$user2Id",
                HttpMethod.DELETE,
                HttpEntity(null, HttpHeaders()),
                ResponseEntity::class.java
        )

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user1Id", List::class.java)
        assertEquals(0, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/user/$user2Id", List::class.java)
        assertEquals(0, response.body?.size)
    }

    @Test
    fun `should delete all transactions from a crypto`() {
        val crypto1 =
                cryptoRepository.save(
                        Crypto(
                                "Bitcoin",
                                "BTC",
                                BigDecimal(200.0),
                                BigDecimal(1.0),
                                LocalDateTime.now(),
                                "This is a coin",
                                    "This is a url",
                                    BigDecimal(5.0),
                                    1,
                                    "23456",
                                    BigDecimal(50.0),
                                    100,
                                    10,
                                    true,
                                    "1000000",
                                    "500000",
                                    linksObj,
                                    coinHistoryInstance,
                                    uuid = "123",
                                )
                )
        val crypto2 =
                cryptoRepository.save(
                        Crypto(
                                "Litecoin",
                                "LTC",
                                BigDecimal(100.0),
                                BigDecimal(1.0),
                                LocalDateTime.now(),
                                "This is a coin",
                                    "This is a url",
                                    BigDecimal(5.0),
                                    1,
                                    "23456",
                                    BigDecimal(50.0),
                                    100,
                                    10,
                                    true,
                                    "1000000",
                                    "500000",
                                    linksObj,
                                    coinHistoryInstance,
                                    uuid = "456",
                                )
                )
        val crypto1Id = crypto1.uuid
        val crypto2Id = crypto2.uuid

        saveOneTransaction(crypto = crypto1)
        saveOneTransaction(crypto = crypto1)
        saveOneTransaction(crypto = crypto2)

        var response =
                restTemplate.getForEntity(getRootUrl() + "/crypto/$crypto1Id", List::class.java)
        assertEquals(2, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/crypto/$crypto2Id", List::class.java)
        assertEquals(1, response.body?.size)

        // Delete transactions for crypto 1

        restTemplate.exchange(
                getRootUrl() + "/crypto/$crypto1Id",
                HttpMethod.DELETE,
                HttpEntity(null, HttpHeaders()),
                ResponseEntity::class.java
        )

        response = restTemplate.getForEntity(getRootUrl() + "/crypto/$crypto1Id", List::class.java)
        assertEquals(0, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/crypto/$crypto2Id", List::class.java)
        assertEquals(1, response.body?.size)

        // Delete transactions for crypto 2

        restTemplate.exchange(
                getRootUrl() + "/crypto/$crypto2Id",
                HttpMethod.DELETE,
                HttpEntity(null, HttpHeaders()),
                ResponseEntity::class.java
        )

        response = restTemplate.getForEntity(getRootUrl() + "/crypto/$crypto1Id", List::class.java)
        assertEquals(0, response.body?.size)

        response = restTemplate.getForEntity(getRootUrl() + "/crypto/$crypto2Id", List::class.java)
        assertEquals(0, response.body?.size)
    }
}
