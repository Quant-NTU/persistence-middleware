# Strategy Pipeline

This page details how the Persistence Middleware API microservice interacts with the Strategy Pipeline goal.

## Managing Pipelines
The Persistence Middleware API microservice stores the user's pipeline configurations into the filesystem. 

The majority of its functions are handled by the PipelineController, found at the `sg.com.quantai.middleware.controllers.PipelineController` class.

### Creating / Uploading a Pipeline

By using the HTTP POST endpoint `pipelines/{user_id}`, where `{user_id}` represents the logged user id, and a `sg.com.quantai.middleware.requests.PipelineRequest` in the body, the developer can send information present in the front-end, including the title, description, strategy Ids and portfolio Id.

Upon successful creation of the pipeline, a HTTP response with the status `HttpStatus.CREATED` (201) is returned, and the created pipeline is included in the response body as a JSON object.

### Reading / Loading Pipelines

#### List of User's Pipelines
By using the HTTP GET endpoint `pipeline/user/{user_id}`, where `{user_id}` represents the logged user id, the developer is able to retrieve the list of pipelines owned by the user in a JSON format.

The JSON response is automatically fed with the content of each Pipeline.

#### Details of a Specific Pipeline
By using the HTTP GET endpoint `pipeline/user/{user_id}/{pipeline_id}`, where `{user_id}` represents the logged user id and `{pipeline_id}` represents a pipeline id, the developer is able to retrieve the details of the selected pipeline in a JSON format.

### Updating a Pipeline
The HTTP PUT endpoint `pipeline/user/{user_id}/{pipeline_id}` allows the developer to update the details of the selected pipeline. The `{user_id}` represents the logged-in user ID, and `{pipeline_id}` represents the pipeline ID. The updated pipeline details should be sent in the request body in JSON format. 

Upon successful update, a HTTP response with the status `HttpStatus.OK` (200) is returned, and the updated pipeline is included in the response body as a JSON object.

### Deleting a Pipeline
By using the HTTP DELETE endpoint `pipeline/user/{user_id}/{pipeline_id}`, where `{user_id}` represents the logged user id, and `{pipeline_id}` represents a pipeline id, the developer is able to delete a pipeline owned by the user.

Upon successful deletion of the pipeline, a HTTP response with the status `HttpStatus.OK` (200) is returned, and the response body contains the message "Deleted pipeline `{pipeline_id}`".

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

### Deleting a Strategy Script

By using the HTTP DELETE endpoint `/user/{user_id}/{uid}`, where `{user_id}` represents the logged user id, AND `{uid}` represents the uid of a script, the developer is able to delete a strategy owned by the user. The microservice first checks that the user owns the strategy and then deletes the respective backup script (by the name of `{uid}.bak`) for the script to be deleted.

The JSON response is a HTTP response with content "Deleted strategy `{uid}`".
