#!/bin/bash
#
# Start eBank Monolith with Spring Boot Admin
# Usage:
#   ./start-with-admin.sh              # Start with local profile (default)
#   ./start-with-admin.sh dev          # Start with dev profile (requires Vault)
#   ./start-with-admin.sh prod         # Start with prod profile (requires Vault)
#   ./start-with-admin.sh stop         # Stop all containers
#   ./start-with-admin.sh clean        # Remove volumes and containers
#

set -e

PROFILE="${1:-local}"
COMPOSE_CMD="docker compose"
ADMIN_URL="http://localhost:8090"
APP_URL="http://localhost:8081"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}➜ $1${NC}"
}

# Check if docker is available
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed or not in PATH"
    exit 1
fi

case "$PROFILE" in
    "stop")
        print_header "Stopping all containers..."
        $COMPOSE_CMD down
        print_success "All containers stopped"
        exit 0
        ;;
    "clean")
        print_header "Cleaning up containers and volumes..."
        $COMPOSE_CMD down -v
        print_success "Cleanup complete"
        exit 0
        ;;
    "local")
        print_header "Starting with LOCAL profile (no Vault)"
        print_info "Services: PostgreSQL, Redis, Admin Server, App"

        # Start PostgreSQL and Redis
        echo ""
        print_info "Starting PostgreSQL and Redis..."
        $COMPOSE_CMD up -d postgres redis

        # Wait for services to be healthy
        echo ""
        print_info "Waiting for services to be healthy..."
        sleep 5

        # Start Admin Server
        echo ""
        print_info "Starting Spring Boot Admin Server..."
        $COMPOSE_CMD up -d admin
        sleep 15

        # Verify Admin is up
        if curl -s -o /dev/null -w "%{http_code}" -u admin:admin $ADMIN_URL | grep -q "200\|401"; then
            print_success "Admin Server is running"
        else
            print_error "Admin Server failed to start"
            echo "Check logs: docker logs ebank_admin"
            exit 1
        fi

        # Start the application
        echo ""
        print_info "Starting the application (local profile)..."
        SPRING_PROFILES_ACTIVE=local $COMPOSE_CMD up -d app

        # Wait for app to be ready
        echo ""
        print_info "Waiting for application to be ready (this may take 30-60s)..."
        MAX_ATTEMPTS=60
        ATTEMPT=0
        while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
            if curl -s $APP_URL/actuator/health > /dev/null 2>&1; then
                print_success "Application is running"
                break
            fi
            ATTEMPT=$((ATTEMPT + 1))
            if [ $((ATTEMPT % 5)) -eq 0 ]; then
                print_info "Still waiting... ($ATTEMPT/$MAX_ATTEMPTS)"
            fi
            sleep 1
        done

        if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
            print_error "Application failed to start within timeout"
            echo "Check logs: docker logs ebank_app"
            exit 1
        fi
        ;;
    "dev")
        print_header "Starting with DEV profile (with Vault)"
        print_info "Services: Vault, PostgreSQL, Redis, Admin Server, App"

        # Start Vault
        echo ""
        print_info "Starting Vault..."
        $COMPOSE_CMD --profile vault up -d vault

        # Wait for Vault
        echo ""
        print_info "Waiting for Vault to initialize (30s)..."
        sleep 30

        # Seed Vault
        print_info "Seeding Vault with E2 config..."
        $COMPOSE_CMD --profile vault up -d vault-init
        sleep 10

        # Start PostgreSQL and Redis
        echo ""
        print_info "Starting PostgreSQL and Redis..."
        $COMPOSE_CMD up -d postgres redis
        sleep 5

        # Start Admin Server
        echo ""
        print_info "Starting Spring Boot Admin Server..."
        $COMPOSE_CMD up -d admin
        sleep 15

        # Start the application
        echo ""
        print_info "Starting the application (dev profile)..."
        SPRING_PROFILES_ACTIVE=dev $COMPOSE_CMD up -d app

        # Wait for app
        echo ""
        print_info "Waiting for application to be ready (60-120s with Vault)..."
        MAX_ATTEMPTS=120
        ATTEMPT=0
        while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
            if curl -s $APP_URL/actuator/health > /dev/null 2>&1; then
                print_success "Application is running"
                break
            fi
            ATTEMPT=$((ATTEMPT + 1))
            if [ $((ATTEMPT % 10)) -eq 0 ]; then
                print_info "Still waiting... ($ATTEMPT/$MAX_ATTEMPTS)"
            fi
            sleep 1
        done

        if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
            print_error "Application failed to start within timeout"
            echo "Check logs: docker logs ebank_app"
            exit 1
        fi
        ;;
    "prod")
        print_header "Starting with PROD profile (with Vault)"
        print_info "Services: Vault (E1), PostgreSQL, Redis, Admin Server, App"

        # Start Vault
        echo ""
        print_info "Starting Vault..."
        $COMPOSE_CMD --profile vault up -d vault

        # Wait for Vault
        echo ""
        print_info "Waiting for Vault to initialize (30s)..."
        sleep 30

        # Seed Vault
        print_info "Seeding Vault with E1 (prod) config..."
        $COMPOSE_CMD --profile vault up -d vault-init
        sleep 10

        # Start PostgreSQL and Redis
        echo ""
        print_info "Starting PostgreSQL and Redis..."
        $COMPOSE_CMD up -d postgres redis
        sleep 5

        # Start Admin Server
        echo ""
        print_info "Starting Spring Boot Admin Server..."
        $COMPOSE_CMD up -d admin
        sleep 15

        # Start the application
        echo ""
        print_info "Starting the application (prod profile)..."
        SPRING_PROFILES_ACTIVE=prod VAULT_ENV_ID=E1 $COMPOSE_CMD up -d app

        # Wait for app
        echo ""
        print_info "Waiting for application to be ready (60-120s with Vault)..."
        MAX_ATTEMPTS=120
        ATTEMPT=0
        while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
            if curl -s $APP_URL/actuator/health > /dev/null 2>&1; then
                print_success "Application is running"
                break
            fi
            ATTEMPT=$((ATTEMPT + 1))
            if [ $((ATTEMPT % 10)) -eq 0 ]; then
                print_info "Still waiting... ($ATTEMPT/$MAX_ATTEMPTS)"
            fi
            sleep 1
        done

        if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
            print_error "Application failed to start within timeout"
            echo "Check logs: docker logs ebank_app"
            exit 1
        fi
        ;;
    *)
        print_error "Unknown profile: $PROFILE"
        echo ""
        echo "Usage:"
        echo "  $0              # Start with local profile (default)"
        echo "  $0 dev          # Start with dev profile (requires Vault)"
        echo "  $0 prod         # Start with prod profile (requires Vault)"
        echo "  $0 stop         # Stop all containers"
        echo "  $0 clean        # Remove containers and volumes"
        exit 1
        ;;
esac

# Print summary
echo ""
print_header "✓ Stack is running!"
echo ""
echo -e "${YELLOW}Services:${NC}"
echo "  Admin Server: $ADMIN_URL"
echo "  Application:  $APP_URL"
echo "  PostgreSQL:   localhost:5432"
echo "  Redis:        localhost:6379"
if [ "$PROFILE" != "local" ]; then
    echo "  Vault:        http://localhost:8200"
fi
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "  1. Open Spring Admin: $ADMIN_URL"
echo "  2. Login with: admin / admin"
echo "  3. Click 'ebank-monolith' in the left panel"
echo "  4. Explore the Health, Metrics, and Loggers tabs"
echo ""
echo -e "${YELLOW}To stop: ./start-with-admin.sh stop${NC}"
echo ""
