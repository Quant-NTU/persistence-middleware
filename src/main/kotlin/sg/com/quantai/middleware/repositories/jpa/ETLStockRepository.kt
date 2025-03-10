package sg.com.quantai.middleware.repositories.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import sg.com.quantai.middleware.data.jpa.ETLStock
import java.sql.Timestamp

@Repository
interface ETLStockRepository : JpaRepository<ETLStock, Long> {

    @Query("SELECT t FROM ETLStock t WHERE t.ticker = :ticker ORDER BY t.date")
    fun findByTicker(ticker: String): List<ETLStock>

    @Query("SELECT DISTINCT t.ticker FROM ETLStock t ORDER BY t.ticker")
    fun findDistinctTickers(): List<String>

    @Query("SELECT t FROM ETLStock t WHERE t.date BETWEEN :startDate AND :endDate ORDER BY t.ticker, t.date")
    fun findByDateRange(startDate: Timestamp, endDate: Timestamp): List<ETLStock>

    @Query("SELECT t FROM ETLStock t WHERE t.ticker = :ticker AND t.date BETWEEN :startDate AND :endDate ORDER BY t.date")
    fun findByTickerAndDateRange(ticker: String, startDate: Timestamp, endDate: Timestamp): List<ETLStock>

    @Query(value = """
        SELECT t.* FROM transformed_stock_data t
        JOIN (
            SELECT ticker, MAX(date) as latest_date 
            FROM transformed_stock_data 
            GROUP BY ticker
        ) latest ON t.ticker = latest.ticker AND t.date = latest.latest_date
        ORDER BY t.ticker
    """, nativeQuery = true)
    fun findLatestForAllTickers(): List<ETLStock>

    @Query(value = """
        SELECT * FROM transformed_stock_data 
        WHERE ticker = :ticker 
        ORDER BY date DESC 
        LIMIT :limit
    """, nativeQuery = true)
    fun findRecentByTicker(ticker: String, limit: Int): List<ETLStock>
}