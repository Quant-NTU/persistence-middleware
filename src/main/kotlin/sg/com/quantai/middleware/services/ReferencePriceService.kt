package sg.com.quantai.middleware.services

import org.springframework.stereotype.Service
import sg.com.quantai.middleware.repositories.jpa.TransformedCryptoRepository
import sg.com.quantai.middleware.repositories.jpa.TransformedForexRepository
import sg.com.quantai.middleware.repositories.jpa.TransformedStockRepository
import java.math.BigDecimal
import java.time.Instant

data class ReferencePrice(
    val symbol: String,
    val price: BigDecimal,
    val asOf: Instant,
)

@Service
class ReferencePriceService(
    private val stockRepository: TransformedStockRepository,
    private val cryptoRepository: TransformedCryptoRepository,
    private val forexRepository: TransformedForexRepository,
) {
    fun latestPrice(symbol: String): ReferencePrice? {
        val sym = symbol.trim().uppercase()
        if (sym.isEmpty()) return null

        stockRepository.findBySymbolOrderByTimestampDesc(sym).firstOrNull()?.let {
            return ReferencePrice(sym, BigDecimal.valueOf(it.close), it.timestamp.toInstant())
        }

        cryptoRepository.findBySymbolAndCurrency(sym, "USD").maxByOrNull { it.timestamp }?.let {
            return ReferencePrice(sym, BigDecimal.valueOf(it.close), it.timestamp.toInstant())
        }

        forexRepository.findByCurrencyPairOrderByTimestampDesc(sym).firstOrNull()?.let {
            return ReferencePrice(sym, BigDecimal.valueOf(it.close), it.timestamp.toInstant())
        }

        return null
    }
}
