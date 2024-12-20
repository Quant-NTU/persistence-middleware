package sg.com.quantai.middleware.data

import sg.com.quantai.middleware.data.Asset
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "NewCrypto")
class NewCrypto extends Asset()
