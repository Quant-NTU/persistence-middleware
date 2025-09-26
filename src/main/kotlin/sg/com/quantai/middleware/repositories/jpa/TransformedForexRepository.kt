package sg.com.quantai.middleware.repositories.jpa

import sg.com.quantai.middleware.data.jpa.TransformedForex
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.sql.Timestamp

interface TransformedForexRepository : JpaRepository<TransformedForex, Long> {

    fun findByCurrencyPair(currencyPair: String): List<TransformedForex>

    @Query("SELECT t FROM TransformedForex t WHERE t.timestamp BETWEEN :startTime AND :endTime")
    fun findByTimestampRange(@Param("startTime") startTime: Timestamp, @Param("endTime") endTime: Timestamp): List<TransformedForex>

    @Query("SELECT t FROM TransformedForex t ORDER BY t.timestamp DESC")
    fun findRecent(): List<TransformedForex>

    @Query("SELECT t FROM TransformedForex t WHERE t.currencyPair = :currencyPair ORDER BY t.timestamp DESC")
    fun findByCurrencyPairOrderByTimestampDesc(@Param("currencyPair") currencyPair: String): List<TransformedForex>
}
