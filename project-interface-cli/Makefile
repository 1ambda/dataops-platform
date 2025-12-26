# Makefile for project-interface-cli (DataOps CLI)
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
	rm -rf dist_standalone/
	rm -rf test_install_env/
	rm -rf profile.stats

# CLI specific targets
build:  ## Build the CLI package
	uv build

build-standalone:  ## Build standalone executable with PyInstaller
	uv run python build_standalone.py

install-local:  ## Install CLI locally for development
	uv sync
	uv run pip install -e .

run:  ## Run CLI with help command (default)
	uv run dli --help

run-cli:  ## Run CLI with example command
	uv run dli --help

test-cli:  ## Test CLI installation and basic functionality
	uv run dli version
	uv run dli sql-parse "SELECT * FROM users"

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

# Distribution targets
dist-check:  ## Check distribution package
	uv build
	uv run twine check dist/*

# Performance profiling
profile:  ## Run CLI with profiling
	uv run python -m cProfile -o profile.stats -m dli.main --help
	uv run python -c "import pstats; p = pstats.Stats('profile.stats'); p.sort_stats('cumulative').print_stats(20)"

# Release targets
prepare-release: clean check-all build dist-check  ## Prepare for release (run all checks and build)

# Development utilities
dev-setup:  ## Set up development environment
	uv sync
	uv run pre-commit install || echo "pre-commit not available, skipping"

demo:  ## Run CLI demo commands
	@echo "=== DataOps CLI Demo ==="
	uv run dli version
	@echo "\n=== Health Check (will fail if server not running) ==="
	uv run dli health || echo "Server not running - this is expected"
	@echo "\n=== SQL Parse Demo ==="
	uv run dli sql-parse "SELECT id, name, created_at FROM users WHERE active = true ORDER BY created_at DESC LIMIT 10"