package sg.com.quantai.middleware.services

import org.springframework.stereotype.Service
import sg.com.quantai.middleware.data.mongo.Asset
import sg.com.quantai.middleware.data.mongo.Crypto
import sg.com.quantai.middleware.data.mongo.Forex
import sg.com.quantai.middleware.data.mongo.Portfolio
import sg.com.quantai.middleware.data.mongo.PortfolioHistory
import sg.com.quantai.middleware.data.mongo.Stock
import sg.com.quantai.middleware.data.mongo.enums.AssetType
import sg.com.quantai.middleware.data.mongo.enums.OrderSide
import sg.com.quantai.middleware.data.mongo.enums.PortfolioActionEnum
import sg.com.quantai.middleware.repositories.mongo.CryptoRepository
import sg.com.quantai.middleware.repositories.mongo.ForexRepository
import sg.com.quantai.middleware.repositories.mongo.PortfolioHistoryRepository
import sg.com.quantai.middleware.repositories.mongo.PortfolioRepository
import sg.com.quantai.middleware.repositories.mongo.StockRepository
import sg.com.quantai.middleware.repositories.mongo.UserRepository
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class PortfolioBookingService(
    private val portfolioRepository: PortfolioRepository,
    private val portfolioHistoryRepository: PortfolioHistoryRepository,
    private val stockRepository: StockRepository,
    private val cryptoRepository: CryptoRepository,
    private val forexRepository: ForexRepository,
    private val userRepository: UserRepository,
) {

    /**
     * Books a trade against a portfolio: upserts the asset, appends a
     * PortfolioHistory entry and adjusts the cash balance. The signed history
     * quantity (positive for BUY, negative for SELL) matches the existing
     * buyAsset/sellAsset convention.
     */
    fun bookTrade(
        userId: String,
        portfolioId: String,
        assetType: AssetType,
        name: String?,
        symbol: String,
        side: OrderSide,
        quantity: BigDecimal,
        price: BigDecimal,
    ): Portfolio {
        val user = userRepository.findOneByUid(userId)
        val portfolio = portfolioRepository.findOneByUid(portfolioId)

        val asset = resolveAsset(assetType, name ?: symbol, symbol, quantity, price)

        val totalValue = quantity.multiply(price)
        val action = if (side == OrderSide.BUY) PortfolioActionEnum.BUY_REAL_ASSET else PortfolioActionEnum.SELL_REAL_ASSET
        val signedQuantity = if (side == OrderSide.BUY) quantity else quantity.negate()

        portfolioHistoryRepository.save(
            PortfolioHistory(
                asset = asset,
                action = action,
                quantity = signedQuantity,
                value = totalValue,
                portfolio = portfolio,
            )
        )

        val newCash = if (side == OrderSide.BUY) {
            portfolio.cashBalance.subtract(totalValue)
        } else {
            portfolio.cashBalance.add(totalValue)
        }

        return portfolioRepository.save(
            portfolio.copy(cashBalance = newCash, updatedDate = LocalDateTime.now())
        )
    }

    /** Net holding quantity of the given asset name across portfolio history. */
    fun holdings(portfolio: Portfolio, assetName: String): BigDecimal {
        val history = portfolioHistoryRepository.findByPortfolio(portfolio)
        var qty = BigDecimal.ZERO
        for (h in history) {
            if (h.asset.name == assetName) {
                if (h.action == PortfolioActionEnum.ADD_MANUAL_ASSET || h.action == PortfolioActionEnum.BUY_REAL_ASSET) {
                    qty = qty.add(h.quantity)
                } else {
                    qty = qty.subtract(h.quantity)
                }
            }
        }
        return qty
    }

    private fun resolveAsset(
        assetType: AssetType,
        name: String,
        symbol: String,
        quantity: BigDecimal,
        price: BigDecimal,
    ): Asset {
        return when (assetType) {
            AssetType.STOCK -> {
                if (!stockRepository.existsByName(name)) {
                    stockRepository.save(Stock(name = name, quantity = quantity, purchasePrice = price, ticker = symbol))
                }
                stockRepository.findByName(name)
            }
            AssetType.CRYPTO -> {
                if (!cryptoRepository.existsByName(name)) {
                    cryptoRepository.save(Crypto(name = name, quantity = quantity, purchasePrice = price, symbol = symbol))
                }
                cryptoRepository.findByName(name)
            }
            AssetType.FOREX -> {
                if (!forexRepository.existsByName(name)) {
                    forexRepository.save(Forex(name = name, quantity = quantity, purchasePrice = price, currencyPair = symbol))
                }
                forexRepository.findByName(name)
            }
        }
    }
}
