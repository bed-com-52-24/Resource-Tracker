#!/usr/bin/env bash

set -euo pipefail

# ==============================================================================
# Pseah Networks - Application Startup Script
#
# Usage:
#   ./start.sh development
#   ./start.sh production
#
# Environment files:
#   development -> .env
#   production  -> .env.production
# ==============================================================================

ENVIRONMENT="${1:-development}"

# ------------------------------------------------------------------------------
# Environment configuration
# ------------------------------------------------------------------------------

case "$ENVIRONMENT" in

    development)
        ENV_FILE=".env"
        START_MODE="spring"
        ;;

    production)
        ENV_FILE=".env.production"
        START_MODE="docker"
        ;;

    *)
        echo "ERROR: Unsupported environment: $ENVIRONMENT"
        echo
        echo "Usage:"
        echo "  $0 development"
        echo "  $0 production"
        exit 1
        ;;

esac

echo "============================================================"
echo "Pseah Networks"
echo "============================================================"
echo "Environment : $ENVIRONMENT"
echo "Env file    : $ENV_FILE"
echo "Start mode  : $START_MODE"
echo "============================================================"


# ------------------------------------------------------------------------------
# Check environment file
# ------------------------------------------------------------------------------

if [[ ! -f "$ENV_FILE" ]]; then
    echo
    echo "ERROR: Environment file '$ENV_FILE' was not found."
    echo
    echo "Create it from the example file:"
    echo
    if [[ "$ENVIRONMENT" == "development" ]]; then
        echo "  cp .env.example .env"
    else
        echo "  cp .env.example .env.production"
    fi
    echo
    exit 1
fi


# ------------------------------------------------------------------------------
# Load environment variables
# ------------------------------------------------------------------------------

echo "Loading environment variables..."

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

echo "Environment variables loaded successfully."


# ------------------------------------------------------------------------------
# Required environment variables
# ------------------------------------------------------------------------------

required_vars=(
    "SPRING_DATASOURCE_URL"
    "SPRING_DATASOURCE_USERNAME"
    "SPRING_DATASOURCE_DRIVER_CLASS_NAME"
    "SPRING_JPA_HIBERNATE_DIALECT"
    "JWT_SECRET_KEY"
    "JWT_ACCESS_TOKEN_EXPIRATION_MS"
    "JWT_REFRESH_TOKEN_EXPIRATION_MS"
    "UPLOAD_DIRECTORY"
    "APP_NAME"
    "APP_PORT"
    "SPRING_PROFILES_ACTIVE"
    "SPRING_FLYWAY_ENABLED"
    "SPRING_FLYWAY_CLEAN_DISABLED"
)

# Production additionally requires MySQL container variables.
if [[ "$ENVIRONMENT" == "production" ]]; then
    required_vars+=(
        "MYSQL_DATABASE"
        "MYSQL_USER"
        "MYSQL_PASSWORD"
        "MYSQL_ROOT_PASSWORD"
    )
fi


# ------------------------------------------------------------------------------
# Validate environment
# ------------------------------------------------------------------------------

echo
echo "Validating environment variables..."

missing=0

for var in "${required_vars[@]}"; do

    if [[ -z "${!var:-}" ]]; then
        echo "ERROR: $var is not defined."
        missing=1
    fi

done

if [[ "$missing" -ne 0 ]]; then
    echo
    echo "ERROR: Required environment variables are missing."
    exit 1
fi

echo "Environment validation successful."


# ------------------------------------------------------------------------------
# Helper: extract DB host and port from JDBC URL
# ------------------------------------------------------------------------------

echo
echo "Extracting MySQL connection properties from SPRING_DATASOURCE_URL ..."
echo "URL: $SPRING_DATASOURCE_URL"

extract_db_host() {
    echo "$SPRING_DATASOURCE_URL" | sed -E 's|jdbc:mysql://([^:/]+).*|\1|'
}

extract_db_port() {
    echo "$SPRING_DATASOURCE_URL" | sed -E 's|jdbc:mysql://[^:]+:([0-9]+).*|\1|'
}

extract_db_name() {
    echo "$SPRING_DATASOURCE_URL" | sed -E 's|jdbc:mysql://[^/]+/([^?]+).*|\1|'
}


# ------------------------------------------------------------------------------
# Helper: wait for MySQL to be ready
# ------------------------------------------------------------------------------

wait_for_mysql() {
    local host="$1"
    local port="$2"
    local user="$3"
    local password="$4"
    local retries=30
    local wait=2

    for ((i=1; i<=retries; i++)); do
        if mysqladmin ping -h "$host" -P "$port" -u "$user" --password="$password" --silent 2>/dev/null; then
            echo "MySQL is ready."
            return 0
        fi
        echo "  Attempt $i/$retries — retrying in ${wait}s..."
        sleep "$wait"
    done

    echo "ERROR: MySQL did not become ready in time."
    exit 1
}


# ------------------------------------------------------------------------------
# Helper: create database if it does not exist
# ------------------------------------------------------------------------------

create_database_if_missing() {
    local host="$1"
    local port="$2"
    local user="$3"
    local password="$4"
    local dbname="$5"

    echo
    echo "Ensuring database '$dbname' exists..."

    mysql -h "$host" -P "$port" -u "$user" --password="$password" \
        -e "CREATE DATABASE IF NOT EXISTS \`$dbname\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null

    echo "Database '$dbname' is ready."
}


# ------------------------------------------------------------------------------
# Helper: create or update API user with full privileges on the database
# ------------------------------------------------------------------------------

create_api_user() {
    local root_user="$1"
    local root_password="$2"
    local dbname="$3"
    local api_user="$4"
    local api_password="$5"

    echo
    echo "Creating or updating API user '$api_user'@'%' ..."

    docker compose exec -T mysql mysql -u"$root_user" -p"$root_password" <<EOF
-- Create user if not exists
CREATE USER IF NOT EXISTS '$api_user'@'%'         IDENTIFIED BY '$api_password';
CREATE USER IF NOT EXISTS '$api_user'@'localhost'  IDENTIFIED BY '$api_password';

-- Update password in case it changed
ALTER USER '$api_user'@'%'         IDENTIFIED BY '$api_password';
ALTER USER '$api_user'@'localhost'  IDENTIFIED BY '$api_password';

-- Grant full privileges on the application database
GRANT ALL PRIVILEGES ON \`$dbname\`.* TO '$api_user'@'%';
GRANT ALL PRIVILEGES ON \`$dbname\`.* TO '$api_user'@'localhost';

-- Apply privilege changes immediately
FLUSH PRIVILEGES;
EOF

    echo "API user '$api_user' is ready and has full access to '$dbname'."
}


# ------------------------------------------------------------------------------
# Development
# ------------------------------------------------------------------------------

if [[ "$ENVIRONMENT" == "development" ]]; then

    DB_HOST=$(extract_db_host)
    DB_PORT=$(extract_db_port)
    DB_NAME=$(extract_db_name)

    # Wait for MySQL then ensure database exists.
    wait_for_mysql "$DB_HOST" "$DB_PORT" "$SPRING_DATASOURCE_USERNAME" "$SPRING_DATASOURCE_PASSWORD"
    create_database_if_missing "$DB_HOST" "$DB_PORT" "$SPRING_DATASOURCE_USERNAME" "$SPRING_DATASOURCE_PASSWORD" "$DB_NAME"

    echo
    echo "============================================================"
    echo "Running Flyway migrations"
    echo "============================================================"

    # Ensure Maven wrapper is executable.
    chmod +x ./mvnw

    if [[ "$DB_NAME" ]]; then
        # Drop database and its tables
        echo
        echo "Dropping all tables from '$DB_NAME'"
        echo

        ./mvnw flyway:clean \
                -Dflyway.cleanDisabled='false' \
                -Dflyway.url="$SPRING_DATASOURCE_URL" \
                -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
                -Dflyway.password="$SPRING_DATASOURCE_PASSWORD"

        echo
        echo "Tables dropped from '$DB_NAME' successfully"
    fi

    # Create or recreate all database tables
    echo
    echo "Creating or recreating all tables in '$DB_NAME'"
    echo

    ./mvnw flyway:migrate \
        -Dflyway.url="$SPRING_DATASOURCE_URL" \
        -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
        -Dflyway.password="$SPRING_DATASOURCE_PASSWORD"

    echo "Flyway migrations completed successfully."

    echo
    echo "============================================================"
    echo "Starting Spring Boot application"
    echo "============================================================"

    ./mvnw spring-boot:run

    exit 0
fi


# ------------------------------------------------------------------------------
# Production
# ------------------------------------------------------------------------------

if [[ "$ENVIRONMENT" == "production" ]]; then

    echo
    echo "============================================================"
    echo "Starting production Docker environment"
    echo "============================================================"

    # Verify Docker is available.
    if ! command -v docker >/dev/null 2>&1; then
        echo "ERROR: Docker is not installed or not available in PATH."
        exit 1
    fi

    # Verify Docker Compose is available.
    if ! docker compose version >/dev/null 2>&1; then
        echo "ERROR: Docker Compose is not available."
        exit 1
    fi

    # Check env file is being read
    echo
    echo "Check env file is being read"
    grep -E "MYSQL_ROOT_PASSWORD|MYSQL_USER|MYSQL_PASSWORD|MYSQL_DATABASE" "$ENV_FILE"

    # Start only the MySQL container first.
    echo
    echo "Starting MySQL container..."
    docker compose --env-file "$ENV_FILE" up -d mysql

    DB_HOST=$(extract_db_host)
    DB_PORT=$(extract_db_port)
    DB_NAME=$(extract_db_name)

    # Wait for MySQL to be ready inside the container.
    echo
    echo "Waiting for MySQL container to be ready..."
    until docker compose exec -T mysql mysqladmin ping -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --silent 2>/dev/null; do
        echo "  MySQL not ready yet — retrying in 2s..."
        sleep 2
    done
    echo "MySQL container is ready."

    # Test login after container is ready
    echo
    echo "Test login with 'root' user after container is ready"
    docker compose exec mysql mysql -u "$MYSQL_USER" -p"$MYSQL_ROOT_PASSWORD" -e "SHOW DATABASES;"

    # Ensure the application database exists.
    echo
    echo "Ensuring database '$MYSQL_DATABASE' exists..."
    docker compose exec -T mysql mysql -h"$DB_HOST" -u"$MYSQL_USER" --protocol=TCP -p"$MYSQL_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS \`$MYSQL_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    echo "Database '$MYSQL_DATABASE' is ready."

    # Create or update the API user.
    echo
    echo "Ensuring API user '$SPRING_DATASOURCE_USERNAME' is granted required permissions..."
    create_api_user "root" "$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" "$MYSQL_USER" "$MYSQL_PASSWORD"

    # Run Flyway migrations via Maven before starting the app.
    echo
    echo "============================================================"
    echo "Running Flyway migrations"
    echo "============================================================"

    # Build a host-accessible URL by replacing the Docker service name with 127.0.0.1
    echo
    echo "Replace '//mysql' with '//127.0.0.1' in the Flyway migration command"
    # shellcheck disable=SC2001
    FLYWAY_URL=$(echo "$SPRING_DATASOURCE_URL" | sed 's|//mysql:|//127.0.0.1:|')
    echo "New SPRING_DATASOURCE_URL: $FLYWAY_URL"
    echo

    chmod +x ./mvnw

    # Clean previous database migrations, if any
    if [[ "$DB_NAME" ]]; then
        echo
        echo "Cleaning '$DB_NAME' from previous migration (if any)"
        echo

        ./mvnw flyway:clean \
                -Dflyway.cleanDisabled='false' \
                -Dflyway.url="$FLYWAY_URL" \
                -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
                -Dflyway.password="$SPRING_DATASOURCE_PASSWORD"

        echo
        echo "Flyway cleaned '$DB_NAME' successfully"
    fi

    ./mvnw flyway:migrate \
        -Dflyway.url="$FLYWAY_URL" \
        -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
        -Dflyway.password="$SPRING_DATASOURCE_PASSWORD"

    echo "Flyway migrations completed successfully."


    # Remove orphaned containers, networks, and images
    echo
    echo "Cleaning up orphaned resources (leave volumes intact)..."
    docker compose down --remove-orphans
    docker image prune -f
    echo "Cleanup complete."


    # Now bring up the remaining services.
    echo
    echo "Starting remaining services..."
    docker compose --env-file "$ENV_FILE" up -d --build

    echo
    echo "============================================================"
    echo "Production environment started"
    echo "============================================================"


    echo
    echo "List or running docker processes and resources..."
    docker compose ps

    exit 0
fi