package monolith.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DBRef
import java.math.BigDecimal

@Document(collection = "accountInfo")
data class AccountInfo (
        val accountValue: MutableList<BigDecimal> = MutableList(12) { BigDecimal.ZERO },
        
        @DBRef
        val owner: User,

        @Indexed(unique = true)
        val uid: String = ObjectId.get().toString(),

        @Id
        val _id: ObjectId = ObjectId.get() // document id, it changes when updated via upsert
)