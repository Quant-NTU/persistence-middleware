package sg.com.quantai.middleware.requests

import sg.com.quantai.middleware.data.enums.StrategyInterval
import sg.com.quantai.middleware.data.enums.StrategyStatus
import org.springframework.data.mongodb.core.mapping.Document
import sg.com.quantai.middleware.requests.TransactionRequest

class StrategyFileRequest(
        val title: String,
        val script: String,
        val uid: String?,
)