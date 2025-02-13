package sg.com.quantai.middleware.repositories.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import sg.com.quantai.middleware.data.jpa.TransformedCrypto
import java.sql.Timestamp

@Repository
interface TransformedCryptoRepository : JpaRepository<TransformedCrypto, Long> {

    @Query("SELECT t FROM TransformedCrypto t WHERE t.symbol = :symbol AND t.currency = :currency")
    fun findBySymbolAndCurrency(symbol: String, currency: String): List<TransformedCrypto>

    @Query("SELECT t FROM TransformedCrypto t WHERE t.timestamp BETWEEN :startTime AND :endTime")
    fun findByTimestampRange(startTime: Timestamp, endTime: Timestamp): List<TransformedCrypto>

    @Query("SELECT t FROM TransformedCrypto t ORDER BY t.timestamp DESC LIMIT 100")
    fun findRecent(): List<TransformedCrypto>
}