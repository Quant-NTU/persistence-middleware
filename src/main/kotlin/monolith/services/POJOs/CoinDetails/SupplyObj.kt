package monolith.services.POJOS.CoinDetails

data class SupplyObj (
        val confirmed: Boolean,
        val supplyAt: Int,
        val total: String?,
        val circulating: String,
        val max: String?
)