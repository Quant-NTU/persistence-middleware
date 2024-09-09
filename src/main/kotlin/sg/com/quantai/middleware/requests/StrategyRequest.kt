package sg.com.quantai.middleware.requests

import sg.com.quantai.middleware.data.enums.StrategyInterval
import sg.com.quantai.middleware.data.enums.StrategyStatus

class StrategyRequest(
        val title: String,
        val script: String,
        val interval: StrategyInterval,
        val status: StrategyStatus,
        val uid: String?,
        val transactions: List<TransactionRequest>? = null 
)