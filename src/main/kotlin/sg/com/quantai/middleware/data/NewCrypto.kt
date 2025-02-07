package sg.com.quantai.middleware.data

import sg.com.quantai.middleware.data.Asset
import org.springframework.data.mongodb.core.mapping.Document
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.annotation.Id
import java.math.BigDecimal

@Document(collection = "NewCrypto")
data class NewCrypto(
    @Id val _id: ObjectId = ObjectId.get(),
    @Indexed(unique = true) val uid: String = ObjectId.get().toString(),
    override val name: String?,
    override val symbol: String?,
    override val quantity: BigDecimal,
    override val purchasePrice: BigDecimal,
): Asset(name, symbol, quantity, purchasePrice) 