package sg.com.quantai.middleware.data

import sg.com.quantai.middleware.data.enums.Assets
import java.math.BigDecimal

@Document(collection = "NewStock")
class NewStock extends Asset(
    //val dividends: BigDecimal == 0
) 