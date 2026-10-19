package sg.com.quantai.middleware.requests

import java.math.BigDecimal

/**
 * Payload pushed by the EMS to the middleware when a FIX ExecutionReport or
 * OrderCancelReject is received. Field names mirror the FIX execution-report
 * semantics.
 */
data class OrderFillRequest(
    val orderId: String? = null,          // ClOrdID (11)
    val exchangeOrderId: String? = null,  // OrderID (37)
    val execId: String? = null,           // ExecID (17)
    val execType: String? = null,         // ExecType (150)
    val ordStatus: String? = null,        // OrdStatus (39)
    val cumulativeQuantity: BigDecimal? = null,
    val leavesQuantity: BigDecimal? = null,
    val averagePrice: BigDecimal? = null,
    val lastPrice: BigDecimal? = null,
    val lastQuantity: BigDecimal? = null,
    val text: String? = null,
    val originalOrderId: String? = null,
)
