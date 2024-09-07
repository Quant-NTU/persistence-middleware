package sg.com.quantai.middleware.requests

import sg.com.quantai.middleware.data.Crypto
import sg.com.quantai.middleware.data.Strategy
import sg.com.quantai.middleware.data.Stock
import sg.com.quantai.middleware.data.enums.TransactionStatus
import sg.com.quantai.middleware.data.enums.TransactionType
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