package sg.com.quantai.middleware.config

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component
import sg.com.quantai.middleware.data.mongo.Crypto
import sg.com.quantai.middleware.data.mongo.Forex
import sg.com.quantai.middleware.data.mongo.Stock

@Component
class MongoIndexInitializer(private val mongo: MongoTemplate) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        // Drop unique asset.uid index created by the previous build
        val history = mongo.indexOps("portfolioHistory")
        history.indexInfo
            .filter { index ->
                index.isUnique &&
                index.indexFields.size == 1 &&
                index.indexFields[0].key == "asset.uid"
            }
            .forEach { history.dropIndex(it.name) }

        // Restore uid uniqueness on the asset collections only
        listOf(Stock::class.java, Crypto::class.java, Forex::class.java).forEach {
            mongo.indexOps(it).ensureIndex(Index().on("uid", Sort.Direction.ASC).unique().named("uid"))
        }
    }
}
