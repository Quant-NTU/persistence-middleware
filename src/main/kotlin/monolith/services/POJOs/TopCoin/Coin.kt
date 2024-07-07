package monolith.services.POJOS.TopCoin

import java.math.BigDecimal

data class Coin (
        val uuid: String,
        val symbol: String,
        val name: String,
        val color: String?,
        val iconUrl: String,
        val marketCap: String,
        val price: BigDecimal,
        val listedAt: Int,
        val change: BigDecimal,
        val rank: Int,
        val sparkline: List<BigDecimal>,
        val lowVolume: Boolean,
        val coinrankingUrl: String,
        val `24hVolume`: String,
        val btcPrice: BigDecimal
)