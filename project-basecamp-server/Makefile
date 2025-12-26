# Makefile for project-basecamp-server
# Spring Boot 4 + Kotlin 2 Multi-Module Gradle Project

.PHONY: help install build test run clean check-all jar docker

help:  ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-20s %s\n", $$1, $$2}'

# Basic Development Commands
install:  ## Download dependencies and build project
	./gradlew build --no-daemon

build:  ## Build all modules
	./gradlew build --no-daemon

clean:  ## Clean build artifacts
	./gradlew clean --no-daemon
	rm -rf build/
	rm -rf */build/

test:  ## Run all tests
	./gradlew test --no-daemon

test-unit:  ## Run unit tests only
	./gradlew test --no-daemon --tests "*Test"

test-integration:  ## Run integration tests only
	./gradlew test --no-daemon --tests "*IntegrationTest"

# Application Runtime Commands
run:  ## Run application with local profile (default)
	./gradlew bootRun --no-daemon

run-local:  ## Run application with local profile
	./gradlew bootRun --no-daemon --args='--spring.profiles.active=local'

run-dev:  ## Run application with dev profile
	./gradlew bootRun --no-daemon --args='--spring.profiles.active=dev'

run-test:  ## Run application with test profile
	./gradlew bootRun --no-daemon --args='--spring.profiles.active=test'

run-debug:  ## Run application in debug mode (port 5005)
	./gradlew bootRun --no-daemon --debug-jvm

# Build Artifacts
jar:  ## Build executable JAR file
	./gradlew bootJar --no-daemon

jar-plain:  ## Build plain JAR files for all modules
	./gradlew jar --no-daemon

assemble:  ## Assemble all outputs
	./gradlew assemble --no-daemon

# Code Quality & Analysis
check:  ## Run all checks (test + code quality)
	./gradlew check --no-daemon

check-all: clean build test check  ## Run complete verification

ktlint:  ## Run Kotlin linter (if configured)
	./gradlew ktlintCheck --no-daemon || echo "ktlint not configured, skipping..."

ktlint-fix:  ## Auto-fix Kotlin style issues (if configured)
	./gradlew ktlintFormat --no-daemon || echo "ktlint not configured, skipping..."

detekt:  ## Run Detekt static analysis (if configured)
	./gradlew detekt --no-daemon || echo "detekt not configured, skipping..."

# Dependencies Management
deps:  ## Show dependency tree
	./gradlew dependencies --no-daemon

deps-outdated:  ## Check for outdated dependencies (if plugin available)
	./gradlew dependencyUpdates --no-daemon || echo "Dependency updates plugin not available"

deps-refresh:  ## Refresh dependencies
	./gradlew build --refresh-dependencies --no-daemon

# Database & Migration Commands
flyway-info:  ## Show Flyway migration status (if configured)
	./gradlew flywayInfo --no-daemon || echo "Flyway not configured"

flyway-migrate:  ## Run Flyway database migrations (if configured)
	./gradlew flywayMigrate --no-daemon || echo "Flyway not configured"

flyway-clean:  ## Clean database schema (if configured)
	./gradlew flywayClean --no-daemon || echo "Flyway not configured"

# Development Utilities
ide-setup:  ## Generate IDE configuration files
	./gradlew idea --no-daemon

modules:  ## List all modules in the project
	@echo "Project modules:"
	@echo "  ├── module-core-common      # Shared utilities and constants"
	@echo "  ├── module-core-domain      # Business domain models"
	@echo "  ├── module-core-infra       # Infrastructure layer"
	@echo "  └── module-server-api       # REST API and web layer"

health:  ## Check if application is running (requires running server)
	@echo "Checking application health..."
	@curl -f http://localhost:8080/api/health 2>/dev/null || echo "Application not running or health endpoint unavailable"

status:  ## Show application endpoints (requires running server)
	@echo "Application endpoints:"
	@echo "  • API Server:    http://localhost:8080"
	@echo "  • Swagger UI:    http://localhost:8080/swagger-ui.html"
	@echo "  • Health Check:  http://localhost:8080/api/health"
	@echo "  • Actuator:      http://localhost:8080/actuator/health"

# CI/CD Commands
ci-build: clean build test check  ## Complete CI build pipeline

ci-package: ci-build jar  ## CI build + package

# Gradle Wrapper Commands
gradle-wrapper:  ## Update Gradle wrapper
	./gradlew wrapper --gradle-version=latest --no-daemon

gradle-version:  ## Show Gradle and project versions
	./gradlew --version --no-daemon
	@echo "Project version: $(shell ./gradlew properties -q | grep "version:" | awk '{print $$2}')"

# Performance & Profiling
build-scan:  ## Build with Gradle build scan (if configured)
	./gradlew build --scan --no-daemon || ./gradlew build --no-daemon

profile-build:  ## Build with Gradle profiling
	./gradlew build --profile --no-daemon

# Docker Support
docker-build:  ## Build Docker image (requires Dockerfile)
	@if [ -f Dockerfile ]; then \
		docker build -t dataops-basecamp-server:latest .; \
	else \
		echo "Dockerfile not found. Create one to enable Docker builds."; \
	fi

docker-run:  ## Run Docker container (requires built image)
	@if docker images | grep -q dataops-basecamp-server; then \
		docker run -p 8080:8080 dataops-basecamp-server:latest; \
	else \
		echo "Docker image not found. Run 'make docker-build' first."; \
	fi

# Docker Compose Commands
compose-up:  ## Start all services with Docker Compose (requires docker-compose.yml)
	@if [ -f docker-compose.yml ] || [ -f docker-compose.yaml ]; then \
		docker-compose up -d; \
		echo "✅ Services started. Use 'make compose-logs' to see logs."; \
	else \
		echo "❌ docker-compose.yml not found. Create one to use Docker Compose."; \
	fi

compose-down:  ## Stop all Docker Compose services
	@if [ -f docker-compose.yml ] || [ -f docker-compose.yaml ]; then \
		docker-compose down; \
	else \
		echo "❌ docker-compose.yml not found."; \
	fi

compose-logs:  ## Show Docker Compose logs
	@if [ -f docker-compose.yml ] || [ -f docker-compose.yaml ]; then \
		docker-compose logs -f; \
	else \
		echo "❌ docker-compose.yml not found."; \
	fi

compose-restart:  ## Restart all Docker Compose services
	@if [ -f docker-compose.yml ] || [ -f docker-compose.yaml ]; then \
		docker-compose restart; \
	else \
		echo "❌ docker-compose.yml not found."; \
	fi

# Troubleshooting
gradle-stop:  ## Stop Gradle daemon
	./gradlew --stop

gradle-clean-cache:  ## Clean Gradle cache
	rm -rf ~/.gradle/caches/
	rm -rf .gradle/

logs:  ## Show application logs (if running)
	@echo "Recent application logs:"
	@tail -f logs/application.log 2>/dev/null || echo "Log file not found. Check if application is running and logging is configured."

logs-error:  ## Show error logs only
	@echo "Recent error logs:"
	@tail -f logs/application.log 2>/dev/null | grep -i error || echo "No error logs found or log file not available."

logs-clear:  ## Clear all log files
	@echo "Clearing log files..."
	@rm -rf logs/*.log 2>/dev/null || echo "No log files to clear."
	@echo "✅ Log files cleared."

# Project Management Commands
init:  ## Initialize project for development (first-time setup)
	@echo "🔧 Initializing DataOps Basecamp Server for development..."
	@echo ""
	@echo "1/4 Checking prerequisites..."
	@command -v java >/dev/null 2>&1 || { echo "❌ Java not found. Please install Java 21+"; exit 1; }
	@echo "✅ Java found: $$(java --version | head -n1)"
	@echo ""
	@echo "2/4 Setting up Gradle wrapper..."
	@chmod +x gradlew
	@echo "✅ Gradle wrapper ready"
	@echo ""
	@echo "3/4 Building project..."
	@make install
	@echo ""
	@echo "4/4 Running initial tests..."
	@make test
	@echo ""
	@echo "🎉 Project initialization complete!"
	@echo "💡 Next steps: make run"

reset:  ## Reset project to clean state
	@echo "🔄 Resetting project to clean state..."
	@make gradle-stop
	@make clean
	@make gradle-clean-cache
	@echo "✅ Project reset complete. Run 'make init' to setup again."

dev-env-check:  ## Check development environment status
	@echo "🔍 Development Environment Status Check"
	@echo ""
	@echo "📋 Prerequisites:"
	@command -v java >/dev/null 2>&1 && echo "✅ Java: $$(java --version | head -n1)" || echo "❌ Java not found"
	@command -v git >/dev/null 2>&1 && echo "✅ Git: $$(git --version)" || echo "❌ Git not found"
	@command -v docker >/dev/null 2>&1 && echo "✅ Docker: $$(docker --version)" || echo "⚠️  Docker not found (optional)"
	@echo ""
	@echo "📂 Project Status:"
	@[ -d build ] && echo "✅ Project built" || echo "⚠️  Project not built yet"
	@[ -f gradle.properties ] && echo "✅ Gradle configured" || echo "⚠️  Gradle properties not found"
	@echo ""
	@echo "🔧 Gradle Status:"
	@./gradlew --version --no-daemon | head -n3
	@echo ""
	@echo "🌐 Ports Check:"
	@echo "Checking if port 8080 is available..."
	@! lsof -i :8080 >/dev/null 2>&1 && echo "✅ Port 8080 available" || echo "⚠️  Port 8080 in use"

# Quick Start Guide
quick-start:  ## Quick start guide for new developers
	@echo "🚀 DataOps Basecamp Server - Quick Start Guide"
	@echo ""
	@echo "1. First time setup:"
	@echo "   make init             # Complete initialization (recommended)"
	@echo "   # OR manually:"
	@echo "   make install          # Build project"
	@echo "   make dev-env-check    # Verify environment"
	@echo ""
	@echo "2. Run application:"
	@echo "   make run              # Default local profile"
	@echo "   make run-dev          # Development profile"
	@echo "   make run-debug        # Debug mode (port 5005)"
	@echo ""
	@echo "3. Access application:"
	@echo "   make status           # Show all endpoints"
	@echo "   make health           # Check health endpoint"
	@echo ""
	@echo "4. Development workflow:"
	@echo "   make test             # Run tests"
	@echo "   make check-all        # Full verification"
	@echo "   make jar              # Build production JAR"
	@echo ""
	@echo "5. Project structure:"
	@echo "   make modules          # Show module overview"
	@echo ""
	@echo "6. Troubleshooting:"
	@echo "   make logs             # View logs"
	@echo "   make reset            # Clean reset if issues"