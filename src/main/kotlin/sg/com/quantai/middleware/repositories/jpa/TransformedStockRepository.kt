package sg.com.quantai.middleware.repositories.jpa

import sg.com.quantai.middleware.data.jpa.TransformedStock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.sql.Timestamp

interface TransformedStockRepository : JpaRepository<TransformedStock, Long> {

    fun findBySymbol(symbol: String): List<TransformedStock>

    @Query("SELECT t FROM TransformedStock t WHERE t.timestamp BETWEEN :startTime AND :endTime")
    fun findByTimestampRange(@Param("startTime") startTime: Timestamp, @Param("endTime") endTime: Timestamp): List<TransformedStock>

    @Query("SELECT t FROM TransformedStock t ORDER BY t.timestamp DESC")
    fun findRecent(): List<TransformedStock>

    @Query("SELECT t FROM TransformedStock t WHERE t.symbol = :symbol ORDER BY t.timestamp DESC")
    fun findBySymbolOrderByTimestampDesc(@Param("symbol") symbol: String): List<TransformedStock>
}
