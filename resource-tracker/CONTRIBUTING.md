# Contributing

## Project conventions

- Keep code in the existing feature package (`auth`, `resource`, `project`, etc.); shared API contracts belong in `common`.
- Name request and response DTOs by purpose, for example `CreateResourceRequest` and `ResourceResponse`.
- Name Flyway migrations `V{number}__short_description.sql` and never edit an already-applied migration.
- Use the shared exceptions in `com.resourcetracker.common` and preserve the standard error response contract.
- Run the relevant Maven tests before opening a pull request.

## Branches and pull requests

- Create a focused branch from the latest `main`, using `feature/<issue>-short-description` or `fix/<issue>-short-description`.
- Keep commits focused and use descriptive messages.
- Open a pull request against `main`, summarize the changes and tests, and link the issue with a closing keyword such as `Closes #26`.
