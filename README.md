# Resource-Tracker
The Resource Tracker is a backend RESTful API for submitting and managing resource requests within a growing Data and Machine Learning engineering startup.

## Run in VS Code

Install Java 21, Docker, and the Extension Pack for Java. Open the repository root in VS Code, then use the Command Palette to run one of these tasks:

- `Resource Tracker: Run` starts MySQL with Docker Compose and runs the Spring Boot application.
- `Resource Tracker: Test` runs the Maven test suite.

The `Resource Tracker` launch configuration also supports debugging from the Run and Debug view. The application reads its database settings from `resource-tracker/.env`.

## Authentication response

`POST /api/v1/auth/login` and `POST /api/v1/auth/refresh` return the same token-only response:

```json
{
  "accessToken": "<access-token>",
  "refreshToken": "<refresh-token>",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 1800,
  "refreshTokenExpiresIn": 604800
}
```

The access token is valid for 30 minutes. The login response no longer includes `user`; this is a breaking API contract change for clients that read `response.user`. After login, clients should call `GET /api/v1/auth/me` for the profile, including `id`, `role`, `teams`, and `teamIds`.
