package sg.com.quantai.middleware.data.jpa

import jakarta.persistence.*
import java.sql.Timestamp

@Entity
@Table(name = "transformed_forex_data")
class TransformedForex(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "currency_pair", nullable = false)
    var currencyPair: String,

    @Column(nullable = false)
    var open: Double,

    @Column(nullable = false)
    var high: Double,

    @Column(nullable = false)
    var low: Double,

    @Column(nullable = false)
    var close: Double,

    @Column(name = "avg_price")
    var avgPrice: Double? = null,

    @Column(name = "price_change")
    var priceChange: Double? = null,

    @Column(nullable = false)
    var timestamp: Timestamp
) {
    constructor() : this(
        id = 0,
        currencyPair = "",
        open = 0.0,
        high = 0.0,
        low = 0.0,
        close = 0.0,
        avgPrice = null,
        priceChange = null,
        timestamp = Timestamp(0)
    )
}
