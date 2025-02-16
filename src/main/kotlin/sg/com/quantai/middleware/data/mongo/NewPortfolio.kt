package sg.com.quantai.middleware.data.mongo

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DBRef
import sg.com.quantai.middleware.data.mongo.Asset
import sg.com.quantai.middleware.data.mongo.User
import sg.com.quantai.middleware.data.mongo.PortfolioHistory

@Document(collection = "newPortfolios")
data class NewPortfolio(
    @Id val _id: ObjectId = ObjectId.get(), 
    @Indexed(unique = true) val uid: String = ObjectId.get().toString(),

    val isMain: Boolean,
    val description: String,
    val name: String,

    @DBRef val owner: User,
    @DBRef val history: List<PortfolioHistory>? = emptyList<PortfolioHistory>(),    
    @DBRef val assets: List<Asset>? = emptyList<Asset>(),
)