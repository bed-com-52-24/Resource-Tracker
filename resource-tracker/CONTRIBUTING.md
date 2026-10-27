# Contributing to Resource Tracker

## 1. Introduction

This document explains the development conventions for the Resource Tracker project.
All contributors should follow these guidelines to keep the code consistent,
readable, maintainable, and easy to test.

## 2. Technology Stack

The project uses:

- Kotlin
- Spring Boot
- Maven
- MySQL
- Spring Data JPA
- Flyway for database migrations
- Spring Security for authentication and authorization
- SpringDoc OpenAPI for API documentation

## 3. Package and File Organisation

Organise code into packages according to its responsibility.

Examples:

- `com.resourcetracker.project` — project management
- `com.resourcetracker.team` — team management
- `com.resourcetracker.common` — shared exceptions and error handling
- `com.resourcetracker.config` — application configuration
- `com.resourcetracker.security` — authentication and authorization

Keep related classes together. Avoid placing unrelated features in the same package.

## 4. Naming Conventions

Use descriptive names that explain the purpose of a class or function.

Examples:

- `ProjectController`
- `ProjectService`
- `ProjectRepository`
- `CreateProjectRequest`
- `ProjectResponse`

Use PascalCase for class names and camelCase for functions and variables.

## 5. Controllers, Services, and Repositories

### Controllers

Controllers define HTTP endpoints, accept requests, and return responses.

Controllers should not contain complex business logic.

### Services

Services implement business rules and coordinate application operations.

### Repositories

Repositories handle database access.

Keep each layer focused on its responsibility.

## 6. DTO Conventions

Use request DTOs to receive client input and response DTOs to return data.

Examples:

- `CreateProjectRequest`
- `UpdateProjectRequest`
- `ProjectResponse`

Use validation annotations where appropriate, such as `@NotBlank`, `@NotNull`,
and `@Size`.

Use `@Valid` on controller request bodies when validation is required.

Do not expose internal database entities directly unless the team has explicitly
agreed that this is appropriate.

## 7. API Conventions

Place versioned API endpoints under `/api/v1`.

Use appropriate HTTP methods and status codes.

Examples:

- `GET` — retrieve resources
- `POST` — create resources
- `PUT` — update resources
- `DELETE` — delete resources when supported

Use `201 Created` when a resource is successfully created and `200 OK` when
a request succeeds with a response body.

Keep endpoint paths and request/response formats consistent across modules.

## 8. Error Handling

Use the shared error-handling mechanism in
`com.resourcetracker.common.Errors.kt`.

Do not create separate controller-specific error formats when the global
exception handler can handle the error.

Error responses should follow the shared `ErrorResponse` structure:

- `timestamp`
- `status`
- `error`
- `code`
- `message`
- `path`
- `correlationId`
- `details`

Use meaningful error codes and HTTP status codes.

Never return passwords, access tokens, database credentials, or other secrets
in error responses.

Unexpected errors should be logged appropriately without exposing internal
implementation details to API clients.

## 9. Database Migrations

Use Flyway for database schema changes.

Store migration scripts in:

`src/main/resources/db/migration/`

Follow the naming pattern:

`V<number>__<description>.sql`

For example:

`V10__add_project_audit_fields.sql`

Before creating a migration, check the existing migration files and use the
next available version number.

Do not modify an already-applied migration unless the team has explicitly
agreed on a safe correction strategy.

## 10. OpenAPI Documentation

Document API endpoints using SpringDoc/OpenAPI annotations where appropriate.

Documentation should explain:

- The purpose of each endpoint
- Request parameters and request bodies
- Successful responses
- Expected error responses
- Authentication and authorization requirements

Use the configured JWT bearer authentication scheme for protected endpoints.

## 11. Testing

Add or update tests when changing application behaviour.

Test relevant success cases, validation failures, missing resources, conflicts,
and authorization failures where applicable.

Run the test suite before submitting a pull request:

`./mvnw test`

Make sure the required database and environment variables are configured
when running integration or application-context tests.

Do not submit changes that introduce unexplained test failures.

## 12. Branching and Commits

Use descriptive branch names.

Examples:

- `feature/26-api-conventions-error-handling`
- `feature/25-audit-trail`

Use clear commit messages that explain the change.

Examples:

- `feat(api): standardize error responses`
- `test(api): add global error handler tests`
- `docs: add contribution guidelines`

Follow the team's agreed branch and commit conventions if they differ from
these examples.

## 13. Pull Requests

Before opening a pull request:

1. Review your changes.
2. Run the relevant tests.
3. Check that no secrets or local environment files are included.
4. Explain what changed and why.
5. Reference the related GitHub issue.
6. Request review from the appropriate team members.

Keep pull requests focused on the issue being addressed.

## 14. Security

Never commit passwords, API tokens, private keys, or production credentials.

Use environment variables for sensitive configuration.

Follow the project's authentication and authorization rules when adding
protected endpoints.
