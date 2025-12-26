# Makefile for project-basecamp-connect
# Type safety and code quality automation

.PHONY: help install dev-install lint type-check format test check-all build clean

help:  ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-15s %s\n", $$1, $$2}'

install:  ## Install production dependencies
	uv sync --no-dev

dev-install:  ## Install all dependencies including development tools
	uv sync

lint:  ## Run linting with ruff
	uv run ruff check src tests
	uv run ruff format --check src tests

lint-fix:  ## Run linting with automatic fixes
	uv run ruff check --fix src tests
	uv run ruff format src tests

type-check:  ## Run type checking with pyright
	uv run pyright

format:  ## Format code with black and ruff
	uv run black src tests
	uv run ruff format src tests

test:  ## Run tests with coverage
	uv run pytest --cov=src --cov-report=term-missing --cov-report=html

test-fast:  ## Run tests without coverage
	uv run pytest

check-all: lint type-check test  ## Run all checks (lint, type-check, test)

ci-check: dev-install check-all  ## Run all checks for CI/CD

build:  ## Build the package
	uv build

clean:  ## Clean up cache and temporary files
	rm -rf .pytest_cache/
	rm -rf __pycache__/
	rm -rf src/**/__pycache__/
	rm -rf tests/**/__pycache__/
	rm -rf .mypy_cache/
	rm -rf .ruff_cache/
	rm -rf htmlcov/
	rm -rf .coverage
	rm -rf build/
	rm -rf dist/
	rm -rf *.egg-info/
	rm -rf type-check-report.json
	rm -rf *.db

run-dev:  ## Run the development server
	uv run python main.py

run-prod:  ## Run the production server (install gunicorn first if needed)
	uv add gunicorn --dev
	uv run gunicorn --bind 0.0.0.0:5001 --workers 4 "main:create_app()"

# Type checking specific targets
type-check-strict:  ## Run pyright with strict settings
	uv run pyright --verbose

type-check-report:  ## Generate pyright report
	uv run pyright --outputjson > type-check-report.json

# Security checks
security-check:  ## Run security checks (requires bandit)
	uv run pip install bandit[toml]
	uv run bandit -r src -f json -o security-report.json || true
	uv run bandit -r src

# Dependencies check
deps-check:  ## Check for outdated dependencies
	uv sync --refresh

# Database targets
db-init:  ## Initialize the database
	uv run python -c "from src.connect.database import init_db; init_db()"

db-reset:  ## Reset the database (WARNING: deletes all data)
	rm -f connect.db
	$(MAKE) db-init

# Docker related targets
docker-build:  ## Build Docker image
	docker build -t project-basecamp-connect .

docker-run:  ## Run Docker container
	docker run -p 5001:5001 project-basecamp-connect
