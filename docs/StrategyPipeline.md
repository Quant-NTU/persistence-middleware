# Strategy Pipeline

This page details how the Persistence Middleware API micro-service interacts with the Strategy Pipeline goal.

## Managing Scripts

Together with the Persistence S3 micro-service, the Persistence Middleware API micro-service stores user's strategy scripts into the filesystem, storing only a path reference to the database.

The majority of its functions are handled by the StrategyController, found at the `sg.com.quantai.middleware.controllers.StrategyController` class.

### Creating / Uploading a Strategy Script

By using the HTTP POST endpoint `strategies/file/{user_id}`, where `{user_id}` represents the logged user id, and a `sg.com.quantai.middleware.requests.StrategyFileRequest` in the body, the developer can send information present in the front-end (such as the text body).

The Persistence Middleware API microservice will incorporate it into a python file and save into the system with the help of the Persistence S3 micro-service.

After the file being correctly saved by the Persistence S3 microservice, a register with the strategy python script path is inserted into the database and related to the User.

### Reading / Loading a Strategy Script

By using the HTTP POST endpoint `strategies/user/{user_id}`, where `{user_id}` represents the logged user id, the developer is able to retrieve the list of strategies owned by the user in a JSON format.

The JSON response is automatically fed with the content of each Strategy Python Script.

### Updating a Strategy Script

### Deleting a Strategy Script