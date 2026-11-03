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

## Email delivery

Password setup and reset codes use SMTP outside the `dev` profile. Configure these variables in `resource-tracker/.env` (or the backend environment):

```dotenv
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=your-smtp-username
MAIL_PASSWORD=your-smtp-password
APP_MAIL_FROM=no-reply@example.com
APP_FRONTEND_BASE_URL=https://your-frontend.example.com
```

Use the SMTP host, port, username, and password supplied by your email provider. The app uses STARTTLS on port 587. Outside the `dev` profile, password codes are never written to application logs.
When running with the default `dev` profile, password setup/reset codes are printed to the backend terminal instead of being emailed. This is for local development only; use a non-`dev` profile to deliver email through SMTP.

## Run the API with Docker Compose

From the `resource-tracker` directory, start the database and backend with:

```bash
docker compose up -d
```

The MySQL data is stored in the `mysql_data` named volume. Flyway runs the
database migrations when the backend starts. If a previous database volume was
created from a backup or an older migration set, it can contain application
tables without the corresponding Flyway history. In that case, reset the
development database before starting again:

```bash
docker compose down
docker volume rm resource-tracker_mysql_data
docker compose up -d
```

Only remove the volume when its data is disposable. For a database that must be
preserved, restore a backup whose `flyway_schema_history` matches the migration
files instead of deleting the volume.
