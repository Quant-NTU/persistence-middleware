package monolith.request

import org.springframework.data.mongodb.core.mapping.Document
import monolith.request.TransactionRequest

class StrategyFileRequest(
        val title: String,
        val script: String,
        val uid: String?
)