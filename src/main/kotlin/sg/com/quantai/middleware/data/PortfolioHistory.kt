package sg.com.quantai.middleware.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import java.math.BigDecimal
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.Document
import sg.com.quantai.middleware.data.Asset
import sg.com.quantai.middleware.data.Portfolio

enum class PortfolioAction { BUY, SELL, ADD, REMOVE }
@Document(collection = "portfolioHistory")
data class PortfolioHistory(
    @Id
    val id: ObjectId = ObjectId.get(),
    val asset: Asset,                
    val action: PortfolioAction,
    val quantity: BigDecimal,
    val value: BigDecimal,

    @DBRef(lazy=true) val owner: Portfolio,

    @Id
    val _id: ObjectId = ObjectId.get()
)