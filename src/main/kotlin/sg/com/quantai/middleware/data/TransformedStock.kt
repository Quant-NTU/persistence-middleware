package sg.com.quantai.middleware.data

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.LocalDateTime

@Entity
@Table(name = "transformed_stock_data")
class TransformedStock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false)
    var ticker: String,

    @Column(nullable = false)
    var date: Timestamp,

    @Column(nullable = false)
    var open: Double,

    @Column(nullable = false)
    var high: Double,

    @Column(nullable = false)
    var low: Double,

    @Column(nullable = false)
    var close: Double,

    @Column(nullable = false)
    var volume: Double,

    @Column
    var closeadj: Double? = null,

    @Column(name = "price_change")
    var priceChange: Double? = null,

    @Column
    var volatility: Double? = null,

    @Column
    var vwap: Double? = null,

    @Column(name = "sma_7")
    var sma7: Double? = null,

    @Column(name = "created_at")
    var createdAt: Timestamp = Timestamp.valueOf(LocalDateTime.now())
) {
    constructor() : this(
        id = 0,
        ticker = "",
        date = Timestamp(0),
        open = 0.0,
        high = 0.0,
        low = 0.0,
        close = 0.0,
        volume = 0.0,
        closeadj = null,
        priceChange = null,
        volatility = null,
        vwap = null,
        sma7 = null
    )
}