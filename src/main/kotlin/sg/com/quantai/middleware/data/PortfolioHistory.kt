package sg.com.quantai.middleware.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import java.math.BigDecimal
import org.springframework.data.mongodb.core.mapping.DBRef
import sg.com.quantai.middleware.data.Asset
import sg.com.quantai.middleware.data.NewPortfolio 

enum class PortfolioAction { BUY, SELL, ADD, REMOVE }

data class PortfolioHistory(
    @Id
    val id: ObjectId = ObjectId.get(),
    val asset: Asset,                   //not sure if this is correct but based on theory I think it is                   
    val action: PortfolioAction,
    val quantity: BigDecimal,
    val value: BigDecimal,

    @DBRef(lazy=true) val owner: NewPortfolio,

    @Id
    val _id: ObjectId = ObjectId.get()
)