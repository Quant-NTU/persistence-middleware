# Asset Management in Portfolio  

This document details how users can manually add assets to their portfolios for testing and simulation purposes within the system.  

## Functionality Overview  

Users can manually include different types of assets (crypto, stocks, and forex) into their portfolios. This functionality is useful for testing strategies and running simulations.  

### Asset Classification  

An **Asset** is a superclass representing financial instruments like cryptocurrencies, stocks, and forex data. Manually created assets allow users to experiment with different market conditions and strategy outcomes.  

## API Endpoints  

### Add an Asset to Portfolio  

**Endpoint:**  
`POST /newPortfolios/{user_id}/addAsset`  

**Description:**  
This endpoint allows users to manually add an asset to their portfolio by specifying its type, name, symbol, quantity, and purchase price.  

**Request Parameters:**  

| Parameter  | Type   | Description |
|------------|--------|-------------|
| `user_id`  | String | Unique identifier of the user. |
| `type`     | String | The type of asset (crypto, stock, forex). |
| `name`     | String | The name of the asset. |
| `symbol`   | String | The asset's ticker symbol. |
| `quantity` | Float  | The quantity of the asset owned. |
| `purchasePrice` | Float | The unit price of the asset at purchase time. |

**Request Body Example:**  

```json
{
  "type": "crypto",
  "name": "Bitcoin",
  "symbol": "BTC",
  "quantity": 1.5,
  "purchasePrice": 45000.00
}
```

**Response:**  

- **`200 OK`**: Successfully added the asset to the portfolio. Returns the updated portfolio object.  
- **`404 NOT FOUND`**: The user or portfolio does not exist.  
- **`400 BAD REQUEST`**: Invalid asset type or missing required fields.  

### Processing the Request  

1. The system checks if the user exists. If not, it returns `404 NOT FOUND`.  
2. It retrieves the user's portfolio. If no portfolio is found, it returns `404 NOT FOUND`.  
3. Based on the asset type, it creates a new asset object (Crypto, Stock, or Forex).  
4. The newly created asset is stored in the database.  
5. A portfolio history entry is created to log the addition of the asset.  
6. The asset is added to the user's portfolio, and the updated portfolio is saved.  
7. A response with the updated portfolio is returned.  

**Example Success Response:**  

```json
{
  "id": "portfolio_123",
  "owner": "user_456",
  "assets": [
    {
      "type": "crypto",
      "name": "Bitcoin",
      "symbol": "BTC",
      "quantity": 1.5,
      "purchasePrice": 45000.00
    }
  ],
  "history": [
    {
      "action": "ADD",
      "asset": "BTC",
      "quantity": 1.5,
      "value": 67500.00
    }
  ]
}
```

## Summary  

This functionality enables users to manually add assets for testing and simulations. The API ensures proper validation and logging while allowing users to test strategies using manually created assets.