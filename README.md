# Resource-Tracker
The Resource Tracker is a backend RESTful API intended to formalize the submission, routing, review, approval, rejection, querying, and resubmission of resource requests within a growing Data and Machine Learning engineering startup. 

## Run in VS Code

Install Java 21, Docker, and the Extension Pack for Java. Open the repository root in VS Code, then use the Command Palette to run one of these tasks:

- `Resource Tracker: Run` starts MySQL with Docker Compose and runs the Spring Boot application.
- `Resource Tracker: Test` runs the Maven test suite.

The `Resource Tracker` launch configuration also supports debugging from the Run and Debug view. The application reads its database settings from `resource-tracker/.env`.
