#!/usr/bin/env bash

set -euo pipefail

# ==============================================================================
# Resource tracker - Application Startup Script
#
# Usage:
#   ./start.sh development
#   ./start.sh production
#   ./start.sh build
#   ./start.sh push
#
# Environment files:
#   development -> .env
#   production  -> .env
# ==============================================================================

ENVIRONMENT="${1:-development}"
IMAGE_NAME="jeckman/resource-tracker-backend:v12"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# ----------------------------------------------------------------------------
# Docker image actions
# ----------------------------------------------------------------------------

case "$ENVIRONMENT" in
    build)
        if ! command -v docker >/dev/null 2>&1; then
            echo "ERROR: Docker is not installed or not available in PATH." >&2
            exit 1
        fi
        echo "Building Docker image: $IMAGE_NAME"
        cd "$SCRIPT_DIR"
        docker build -t "$IMAGE_NAME" .
        exit 0
        ;;
    push)
        if ! command -v docker >/dev/null 2>&1; then
            echo "ERROR: Docker is not installed or not available in PATH." >&2
            exit 1
        fi
        echo "Pushing Docker image: $IMAGE_NAME"
        docker push "$IMAGE_NAME"
        exit 0
        ;;
esac

# ------------------------------------------------------------------------------
# Environment configuration
# ------------------------------------------------------------------------------

case "$ENVIRONMENT" in

    development)
        ENV_FILE=".env"
        START_MODE="spring"
        ;;

    production)
        ENV_FILE=".env"
        START_MODE="docker"
        ;;

    *)
        echo "ERROR: Unsupported environment: $ENVIRONMENT"
        echo
        echo "Usage:"
        echo "  $0 development"
        echo "  $0 production"
        echo "  $0 build"
        echo "  $0 push"
        exit 1
        ;;

esac

echo "============================================================"
echo "Resource Tracker Startup Script"
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
    "DB_NAME"
    "DB_USER"
    "DB_PASSWORD"
    "APP_SECURITY_JWT_SECRET"
    "DB_PORT"
    "DATASOURCE_USERNAME"
    "DATASOURCE_PASSWORD"
    "SPRING_DATASOURCE_PASSWORD"
    "SPRING_DATASOURCE_USERNAME"
    "PORT"
    "MAIL_PORT"
    "MAIL_HOST"
    "MAIL_USERNAME"
    "MAIL_PASSWORD"
    "APP_MAIL_FROM"
)

# Use the Spring datasource URL for local development and the deployment URL
# for production migrations.
if [[ "$ENVIRONMENT" == "development" ]]; then
    required_vars+=("SPRING_DATASOURCE_URL")
    DB_CONNECTION_URL="${SPRING_DATASOURCE_URL:-}"
else
    required_vars+=("DATABASE_URL")
    DB_CONNECTION_URL="${DATABASE_URL:-}"
fi

# Production additionally requires MySQL container variables.
if [[ "$ENVIRONMENT" == "production" ]]; then
    required_vars+=(
        "DB_NAME"
        "DB_USER"
        "DB_PASSWORD"
        "DATASOURCE_USERNAME"
        "DATASOURCE_PASSWORD"
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
echo "Extracting MySQL connection properties from the configured datasource URL ..."

extract_db_host() {
    echo "$DB_CONNECTION_URL" | sed -E 's|jdbc:mysql://([^:/]+).*|\1|'
}

extract_db_port() {
    echo "$DB_CONNECTION_URL" | sed -E 's|jdbc:mysql://[^:]+:([0-9]+).*|\1|'
}

extract_db_name() {
    echo "$DB_CONNECTION_URL" | sed -E 's|jdbc:mysql://[^/]+/([^?]+).*|\1|'
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

    # Spring Boot runs Flyway with the application classpath, including the
    # Kotlin-based V22 integer ID migration.
    echo
    echo "Starting Spring Boot application and running Flyway migrations"
    echo

    if false; then
        ./mvnw flyway:migrate \
        -Dflyway.url="$DB_CONNECTION_URL" \
        -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
        -Dflyway.password="$SPRING_DATASOURCE_PASSWORD"

    fi

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
    until docker compose exec -T mysql mysqladmin ping -u"$DB_USER" -p"$DB_PASSWORD" --silent 2>/dev/null; do
        echo "  MySQL not ready yet — retrying in 2s..."
        sleep 2
    done
    echo "MySQL container is ready."

    # Test login after container is ready
    echo
    echo "Test login with 'root' user after container is ready"
    docker compose exec mysql mysql -u "$DB_USER" -p"$DB_PASSWORD" -e "SHOW DATABASES;"

    # Ensure the application database exists.
    echo
    echo "Ensuring database '$DB_NAME' exists..."
    docker compose exec -T mysql mysql -h"$DB_HOST" -u"$DB_USER" --protocol=TCP -p"$DB_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    echo "Database '$DB_NAME' is ready."

    # Now bring up the remaining services.
    echo
    echo "Starting remaining services..."
    docker compose --env-file "$ENV_FILE" up -d --pull always

    echo
    echo "============================================================"
    echo "Production environment started"
    echo "============================================================"


    echo
    echo "List or running docker processes and resources..."
    docker compose ps

    exit 0
fi
