package monolith.services.POJOS.CoinHistory

import java.math.BigDecimal

data class HistoryInstance (
        val price: BigDecimal,
        val timestamp: Int
)