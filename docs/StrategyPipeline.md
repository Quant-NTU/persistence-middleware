# Strategy Pipeline

This page details how the Persistence Middleware API microservice interacts with the Strategy Pipeline goal.

## Managing Scripts

Together with the Persistence S3 microservice, the Persistence Middleware API microservice stores user's strategy scripts into the filesystem, storing only a path reference to the database.

The majority of its functions are handled by the StrategyController, found at the `sg.com.quantai.middleware.controllers.StrategyController` class.

### Creating / Uploading a Strategy Script

By using the HTTP POST endpoint `strategies/file/{user_id}`, where `{user_id}` represents the logged user id, and a `sg.com.quantai.middleware.requests.StrategyFileRequest` in the body, the developer can send information present in the front-end (such as the text body).

The Persistence Middleware API microservice will incorporate it into a python file and save into the system with the help of the Persistence S3 microservice.

After the file being correctly saved by the Persistence S3 microservice, a register with the strategy python script path is inserted into the database and related to the User.

### Reading / Loading a Strategy Script

By using the HTTP POST endpoint `strategies/user/{user_id}`, where `{user_id}` represents the logged user id, the developer is able to retrieve the list of strategies owned by the user in a JSON format.

The JSON response is automatically fed with the content of each Strategy Python Script.

### Updating a Strategy Script
To update an existing strategy script, the microservice uses the HTTP POST endpoint `/file/{user_id}`, where `{user_id}` is the ID of the logged-in user. The process checks whether a strategy with the specified `uid` already exists. If it does, the current version of the file is backed up as `{filename}.bak` in S3. The script content is then updated with the new version provided in the request. Upon a successful update, the backup file is deleted. If the update fails, the backup file is restored to its original state.

### Steps for Updating:
1. **Check if the strategy exists**:
    - If the strategy exists, extract its filename from the `path`.

2. **Back up the existing file**:
    - The original file in S3 is renamed to a `.bak` version using the `renameS3File` function. This backs up the file in case of upload failure.

3. **Upload the new content**:
    - The new strategy script is uploaded to S3.

4. **Handle success or failure**:
    - If the upload is successful, the `.bak` file is deleted.
    - If the upload fails, the original file is restored from the `.bak` backup.

5. **Update the strategy in the database**:
    - The strategy details, such as title and update timestamp, are updated in the database.

### Deleting a Strategy Script

By using the HTTP DELETE endpoint `/user/{user_id}/{uid}`, where `{user_id}` represents the logged user id, AND `{uid}` represents the uid of a script, the developer is able to delete a strategy owned by the user. The microservice first checks that the user owns the strategy and then deletes the respective backup script (by the name of `{uid}.bak`) for the script to be deleted.

The JSON response is a HTTP response with content "Deleted strategy `{uid}`".
