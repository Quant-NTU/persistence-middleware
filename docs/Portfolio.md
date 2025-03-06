# Portfolio

The portfolio page serves as the main interface for users to view any information related to their portfolio and the assets in it. Every user has a default portfolio which serves as the base portfolio for users to add assets to.

## Managing the Portfolio

The Portfolio view ( `views/PortfolioView` ) manages all Portfolio configured by the user and communicates with the rest of the platform by interacting with the Persistence Middleware micro-service, which handles pipeline CRUD.

### Viewing all of user's Portfolio(s)

#### Situation 1: User has no portfolio (i.e. user does not even have a default portfolio)

The system will ask user to create a default portfolio.

#### Situation 2: User has portfolio(s)

The system shows all the user's portfolios in a table. Each portfolio shall have the name, description, value, created date, updated date and a list of actions. The actions are:

- view detail of this portfolio, denoted with an eye icon
- edit this portfolio, denoted with a pen icon
- delete this portfolio, denoted with a trash icon

### Creating a Portfolio

To be added

### Editing a Portfolio

The system shows all of the details of the user's portfolio. Users will be able to edit the name, description, as well as quantity of their assets. Assets that are removed from the portfolio are transferred to the default portfolio, and assets that are added to the portfolio are transferred from the default portfolio.

### Deleting a Portfolio

To be added

## Legacy Portfolio Documentations

TODO: Check if this should be removed.

## Goal

1. Minimise the number of clicks required by the user to view all asset-related details.
2. Provide a balance between maximising the screen real estate and conciseness.
3. Provide quick access to additional details or relevant actions to be taken.

## Setup

For the current portfolio page to display information for testing, you must have:

- A valid user
- Assets in either your Stock or Crypto database tables
- Manually insert portfolios linked to your user's id that contain the above asset
- Create a strategy using the trade platform
- Create a trade using the trade overlay

## Implementation

### Most Relevant Files

#### Frontend

_Note: PortfolioTable.tsx is the legacy version that is being replaced_

- PortfolioPage.tsx

#### Backend

_Note: Each backend table generally has data, repository, controller and request classes. Only the base data class and any additional classes beyond these are mentioned._

- persistence-middleware/src/main/kotlin/monolith/data/AccountInfo.kt
- persistence-middleware/src/main/kotlin/monolith/data/Portfolio.kt
- persistence-middleware/src/main/kotlin/monolith/data/Strategy.kt
- persistence-middleware/src/main/kotlin/monolith/data/Transaction.kt
- persistence-middleware/src/main/kotlin/monolith/data/Crypto.kt
- persistence-middleware/src/main/kotlin/monolith/data/Stock.kt

### Cards

#### Account Summary

Component(s): Total Account Value, Percentage Change, Currency, Account Value History Line Graph, Platform Distribution Donut Chart

This card provides a summary of the user's portfolio and its performance. The total account value will be located at the top of the card, along with its percentage change and the currency the account is held in. Subsequently, a line graph documents the total account value change across a 1 year period, followed by a donut chart that will display the value distribution across the various platforms that the assets are held on, rather than asset type.

#### Portfolio Positions

Component(s): Portfolio Table, Trade Buttons

This card provides all the user's portfolio assets. A table containing a list of assets owned by the User within the portfolio is provided along with core financial information pertaining to its price and quantity. A “BUY” and “SELL” button is also provided to allow Users to take immediate action straight from the dashboard, which aims to help reduce the effective time taken to trade. The portfolio positions also provide grouped rows for each asset, allowing Users to select and expand the rows to see the breakdown by platform.

#### Orders

Component(s): Order Table

This card provides all the user's active orders. A list of all recent orders and their statuses displayed, which will update Users on their active orders’ progress. The orders are now colour-coded by strategy, allowing Users to easily identify the orders that were created automatically by a strategy.

#### Strategy

Component(s): Strategy Table
This card provides all the user's strategies and a colour label per strategy from which orders can be referenced from.

### Development Notes

1. The currency is currently hardcoded till multi-currency support is introduced.
2. Tables are implemented using AG-Grid, which was chosen due to its performance with rapid data updates, however no license has been acquired yet which needs to be either purchased or replaced before deployment.
3. The current page is not developed with responsive design in mind (Smaller screens will have issues displaying correctly).

## Future Improvements

- Multi-currency support
- Multi-portfolio support (Project Advisor recommendation)
- Real-time updating of the asset prices
- Dynamic graphs which can be filtered to display different ranges/data
