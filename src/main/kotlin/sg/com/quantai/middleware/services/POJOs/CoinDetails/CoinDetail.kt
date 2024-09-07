package sg.com.quantai.middleware.services.POJOS.CoinDetails

import java.math.BigDecimal

data class CoinDetail (
        val uuid: String,
        val symbol: String,
        val name: String,
        val description: String,
        val color: String?,
        val iconUrl: String,
        val websiteUrl: String,
        val links: List<LinkObj>,
        val supply: SupplyObj,
        val marketCap: String,
        val fullyDilutedMarketCap: String,
        val price: BigDecimal,
        val priceAt: Int,
        val change: BigDecimal,
        val rank: Int,
        val numberOfMarkets: Int, 
        val numberOfExchanges: Int,
        val sparkline: List<BigDecimal>,
        val allTimeHigh: AllTimeHighObj,
        val lowVolume: Boolean,
        val coinrankingUrl: String,
        val listedAt: Int,
        val `24hVolume`: String,
        val btcPrice: BigDecimal,
        //val notices: List<NoticeObj>,
        //val tags: List<String>
)