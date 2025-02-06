package sg.com.quantai.middleware.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DBRef
import sg.com.quantai.middleware.data.PortfolioHistory  
import sg.com.quantai.middleware.data.User 

@Document(collection = "new-portfolios")
data class NewPortfolio(
    @Id
    val id: ObjectId = ObjectId.get(),

    val isMain: Boolean,
    
    @DBRef val owner: User, //Needed to map portfolio to user  
    @DBRef(lazy=true) val history: List<PortfolioHistory>? = emptyList<PortfolioHistory>(),

    @Indexed(unique = true)
    val uid: String = ObjectId.get().toString() //assigns uid that mongo uses to identify the object
)
