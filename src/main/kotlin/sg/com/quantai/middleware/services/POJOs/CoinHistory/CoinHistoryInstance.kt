package sg.com.quantai.middleware.services.POJOS.CoinHistory

import java.math.BigDecimal

data class CoinHistoryInstance (
    val change: BigDecimal,
    val history: List<HistoryInstance>
)