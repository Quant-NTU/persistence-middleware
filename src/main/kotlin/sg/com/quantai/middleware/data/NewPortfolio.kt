package sg.com.quantai.middleware.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DBRef
import sg.com.quantai.middleware.data.PortfolioHistory  
import sg.com.quantai.middleware.data.User 

@Document(collection = "portfolios")
data class NewPortfolio(
    @Id
    val id: ObjectId = ObjectId.get(),

    val isMain: Boolean,

    val history: List<PortfolioHistory>,
    
    @DBRef
    val owner: User, //Needed to map portfolio to user  
    
    @Indexed(unique = true)
    val uid: String = ObjectId.get().toString() //Not sure what this does 
)
