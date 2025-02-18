package sg.com.quantai.middleware.data.mongo

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import java.math.BigDecimal
import org.springframework.data.mongodb.core.mapping.Document
import sg.com.quantai.middleware.data.mongo.enums.PortfolioActionEnum
import java.time.LocalDateTime

@Document(collection = "portfolioHistory")
data class PortfolioHistory(
    // Id columns
    @Id val _id: ObjectId = ObjectId.get(),
    @Indexed(unique = true) val uid: String = ObjectId.get().toString(),
    // Database columns
    val asset: Asset,
    val action: PortfolioActionEnum,
    val quantity: BigDecimal,
    val value: BigDecimal,
    // Timestamps columns
    val createdDate: LocalDateTime = LocalDateTime.now(),
    val updatedDate: LocalDateTime = LocalDateTime.now(),
) {

}