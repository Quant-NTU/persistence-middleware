package sg.com.quantai.middleware.requests

import sg.com.quantai.middleware.data.enums.StrategyInterval
import sg.com.quantai.middleware.data.enums.StrategyStatus
import org.springframework.data.mongodb.core.mapping.Document
import sg.com.quantai.middleware.requests.TransactionRequest

class StrategyRequest(
        val title: String,
        val script: String,
        val interval: StrategyInterval,
        val status: StrategyStatus,
        val uid: String?,
        val transactions: List<TransactionRequest>? = null 

)