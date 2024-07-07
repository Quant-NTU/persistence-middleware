package monolith.request

import monolith.data.*
import java.math.BigDecimal

class TransactionRequest(
        val crypto: Crypto? = null,
        val stock: Stock? = null,
        val quantity: BigDecimal,
        val price: BigDecimal,
        val maxBuyPrice: String? = null,
        val minSellPrice: String? = null,
        val type: TransactionType,
        val strategy: Strategy? = null,
        val strategyId: String? = null,
        val status: TransactionStatus
        )