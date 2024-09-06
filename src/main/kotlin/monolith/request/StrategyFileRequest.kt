package monolith.request

import monolith.data.StrategyInterval
import monolith.data.StrategyStatus
import org.springframework.data.mongodb.core.mapping.Document
import monolith.request.TransactionRequest

class StrategyFileRequest(
        val title: String,
        val script: String,
        val interval: StrategyInterval,
        val status: StrategyStatus,
        val uid: String?,
        val transactions: List<TransactionRequest>? = null
)