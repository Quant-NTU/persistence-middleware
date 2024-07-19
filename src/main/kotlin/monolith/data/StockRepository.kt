package monolith.data

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
// import monolith.data.Stock

interface StockRepository : MongoRepository<Stock, String> {
    fun findOneByUuid(uuid: String): Stock
    fun findByUuid(uuid: String): List<Stock>
    fun findOneByName(name: String): Stock
    fun findOneBySymbol(symbol: String): Stock?
    fun findBySymbolIn(symbols: List<String>): List<Stock>
    override fun deleteAll()

}