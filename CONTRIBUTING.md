# Contributing

## Prerequisites

- JDK 21
- Docker Engine or Docker Desktop for integration tests
- Git and `curl` for the lint tools

The Maven wrapper downloads Maven and project dependencies on first use.

## Build and tests

Run the unit tests:

```sh
cd resource-tracker
./mvnw test
```

Run the MySQL integration tests and the service coverage gate:

```sh
./mvnw verify
```

Integration tests use Testcontainers to start MySQL 8.4 and apply the real Flyway migrations. Docker must be running. The `*IT` test classes are run by Maven Failsafe during `verify`; unit tests use the `*Test` naming convention and run under Surefire.

## Writing an integration test

1. Put the test under `resource-tracker/src/test/kotlin` and name it `*IT.kt` so Failsafe runs it.
2. Extend `com.resourcetracker.testsupport.MySqlIntegrationTest`. The base class supplies the MySQL Testcontainer, test profile, repositories, and a helper for issuing real JWTs for each `Role`.
3. Use `TestDataBuilders` for departments, users, and resources. Save records with their repositories so assertions exercise the real schema and constraints.
4. Use the injected `MockMvc` to call the HTTP endpoint with `Authorization: Bearer <token>`. Assert both the status and the response body. For access-control changes, cover permitted and denied roles and department scopes.
5. For state-changing operations, assert the persisted audit or stock-movement entry as well as the HTTP response.

Example:

```kotlin
val department = departments.saveAndFlush(TestDataBuilders.department("Test Operations"))
val token = tokenFor(Role.DEPARTMENT_MANAGER, department)

mockMvc.get("/api/v1/resources") {
    header("Authorization", JwtTestTokens.bearer(token))
}.andExpect {
    status { isOk() }
}
```

Do not use the development data seeder or an H2 substitute in integration tests. The Testcontainers base starts a clean MySQL database and validates production Flyway migrations.

## Lint and coverage

Run formatting and static analysis from the repository root:

```sh
./scripts/lint.sh
```

The script uses pinned ktlint and detekt versions. Existing findings are recorded in baselines; new findings fail CI. After intentionally reviewing and accepting existing findings, regenerate the baselines with `./scripts/lint.sh --update-baseline`. Maven creates an HTML and XML JaCoCo report under `resource-tracker/target/site/jacoco` and enforces at least 70% line coverage across service classes.

## Pull requests

Open pull requests against `main`. CI runs ktlint, detekt, unit and MySQL integration tests, the coverage gate, and a Docker image build. The `quality` check must pass, and `main` requires one approving review before merge.
