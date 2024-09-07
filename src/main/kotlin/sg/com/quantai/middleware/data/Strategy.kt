package sg.com.quantai.middleware.data

import sg.com.quantai.middleware.data.Transaction
import sg.com.quantai.middleware.data.enums.StrategyInterval
import sg.com.quantai.middleware.data.enums.StrategyStatus
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference
import org.springframework.data.mongodb.core.mapping.DBRef
import java.time.LocalDateTime
import java.math.BigDecimal
import kotlin.collections.List

//TODO: Populate from data incoming from the Crypt API webservice
//TODO: We can also save this data into a static file - which should assist our demo presentations
@Document(collection = "strategies")
data class Strategy (
        val title: String,

        val script: String,

        // TODO: Remove from Strategy. This will be handled by the Pipeline
        val interval: StrategyInterval?,

        // TODO: Remove from Strategy. This will be handled by the Pipeline
        val status: StrategyStatus?,

        // TODO: Remove from Strategy. This will be handled by the Pipeline
        var transactionCount: Long = 0,

        @DBRef
        val owner: User,

        val createdDate: LocalDateTime = LocalDateTime.now(),

        val updatedDate: LocalDateTime = LocalDateTime.now(),

        @Indexed(unique = true)
        val uid: String = ObjectId.get().toString(),

        @Id
        val _id: ObjectId = ObjectId.get(), // document id, it changes when updated via upsert


        // @DBRef
        val transactions: MutableList<Transaction> = mutableListOf()
        ) {
                fun addTransaction() {
                        transactionCount++
                }
        }