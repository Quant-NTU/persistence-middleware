package sg.com.quantai.middleware.data.jpa

import jakarta.persistence.*
import java.sql.Timestamp

@Entity
@Table(name = "transformed_stock_data")
class TransformedStock(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false)
    var symbol: String,

    @Column(nullable = false)
    var open: Double,

    @Column(nullable = false)
    var high: Double,

    @Column(nullable = false)
    var low: Double,

    @Column(nullable = false)
    var close: Double,

    @Column(nullable = false)
    var volume: Long,

    @Column(name = "avg_price")
    var avgPrice: Double? = null,

    @Column(name = "price_change")
    var priceChange: Double? = null,

    @Column(nullable = false)
    var timestamp: Timestamp
) {
    constructor() : this(
        id = 0,
        symbol = "",
        open = 0.0,
        high = 0.0,
        low = 0.0,
        close = 0.0,
        volume = 0L,
        avgPrice = null,
        priceChange = null,
        timestamp = Timestamp(0)
    )
}
