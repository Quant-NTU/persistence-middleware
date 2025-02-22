package sg.com.quantai.middleware.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DBRef
import sg.com.quantai.middleware.data.PortfolioHistory  
import sg.com.quantai.middleware.data.User 

@Document(collection = "newPortfolios")
data class NewPortfolio(
    @Id val id: ObjectId = ObjectId.get(),
    val name: String,
    val isMain: Boolean,
    
    @DBRef val owner: User,
    @DBRef(lazy=true) val history: List<PortfolioHistory>? = emptyList<PortfolioHistory>(),
    @DBRef(lazy=true) val assets: List<Asset>? = emptyList<Asset>(),

    @Indexed(unique = true)
    val uid: String = ObjectId.get().toString() //assigns uid that mongo uses to identify the object
)
