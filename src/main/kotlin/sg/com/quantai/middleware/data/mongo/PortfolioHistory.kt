package sg.com.quantai.middleware.data.mongo

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import java.math.BigDecimal
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.Document
import sg.com.quantai.middleware.data.mongo.Asset

enum class PortfolioAction { BUY, SELL, ADD, REMOVE }
@Document(collection = "portfolioHistory")
data class PortfolioHistory(
    @Id val _id: ObjectId = ObjectId.get(),
    @Indexed(unique = true) val uid: String = ObjectId.get().toString(),

    val asset: Asset,                
    val action: PortfolioAction,
    val quantity: BigDecimal,
    val value: BigDecimal,
)