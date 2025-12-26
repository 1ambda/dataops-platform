# Makefile for project-basecamp-ui
# React 19 + Vite + TypeScript + Tailwind CSS Frontend Application

.PHONY: help install build dev test clean check-all preview format lint docker

help:  ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-20s %s\n", $$1, $$2}'

# Basic Development Commands
install:  ## Install dependencies
	pnpm install

install-clean:  ## Clean install dependencies (removes node_modules first)
	rm -rf node_modules pnpm-lock.yaml
	pnpm install

build:  ## Build production bundle
	pnpm run build

clean:  ## Clean build artifacts and dependencies
	rm -rf dist/
	rm -rf node_modules/
	rm -rf .vite/

# Development Commands
dev:  ## Start development server
	pnpm run dev

dev-host:  ## Start development server with host access (0.0.0.0)
	pnpm run dev -- --host

dev-port:  ## Start development server on specific port (e.g., make dev-port PORT=3001)
	pnpm run dev -- --port $(or $(PORT),3000)

preview:  ## Preview production build locally
	pnpm run preview

# Code Quality & Formatting
lint:  ## Run ESLint
	pnpm run lint

lint-fix:  ## Run ESLint with auto-fix
	pnpm run lint -- --fix

format:  ## Format code with Prettier
	pnpm run format

format-check:  ## Check code formatting
	pnpm run format:check

check:  ## Run all checks (lint + format check)
	pnpm run lint
	pnpm run format:check

check-all: clean install build check  ## Run complete verification

# Dependencies Management
deps-update:  ## Update all dependencies to latest
	pnpm update --latest

deps-audit:  ## Run security audit
	pnpm audit

deps-outdated:  ## Check for outdated packages
	pnpm outdated

deps-clean:  ## Clean up unused dependencies
	pnpm run knip

# Build Variants
build-staging:  ## Build for staging environment
	@if [ -f .env.staging ]; then \
		cp .env.staging .env.local && pnpm run build; \
	else \
		echo "No .env.staging file found"; \
	fi

build-production:  ## Build for production environment
	@if [ -f .env.production ]; then \
		cp .env.production .env.local && pnpm run build; \
	else \
		echo "No .env.production file found"; \
	fi

# Testing Commands (placeholder for future test setup)
test:  ## Run tests (placeholder - add test framework like Vitest)
	@echo "⚠️  Tests not configured yet. Consider adding Vitest or Jest for testing."
	@echo "    Add to package.json: pnpm add -D vitest @vitest/ui jsdom"

test-watch:  ## Run tests in watch mode (placeholder)
	@echo "⚠️  Test watch mode not configured yet."

test-coverage:  ## Run tests with coverage (placeholder)
	@echo "⚠️  Test coverage not configured yet."

# Development Utilities
type-check:  ## Run TypeScript type checking
	tsc --noEmit

analyze:  ## Analyze bundle size (if bundle analyzer is configured)
	@echo "📦 Analyzing bundle..."
	@if command -v npx >/dev/null 2>&1; then \
		npx vite-bundle-analyzer dist/; \
	else \
		echo "Install vite-bundle-analyzer: pnpm add -D vite-bundle-analyzer"; \
	fi

serve:  ## Serve production build locally (alternative to preview)
	@if [ -d dist ]; then \
		echo "🚀 Serving production build on http://localhost:4173"; \
		pnpm run preview; \
	else \
		echo "❌ No build found. Run 'make build' first."; \
	fi

# Environment Commands
env-setup:  ## Setup environment file from example
	@if [ ! -f .env.local ] && [ -f .env.example ]; then \
		cp .env.example .env.local; \
		echo "✅ Created .env.local from .env.example"; \
		echo "🔧 Please update .env.local with your configuration"; \
	else \
		echo "⚠️  .env.local already exists or .env.example not found"; \
	fi

env-check:  ## Check environment configuration
	@echo "📋 Environment files:"
	@ls -la .env* 2>/dev/null || echo "No .env files found"
	@echo ""
	@echo "🔍 Current NODE_ENV: $${NODE_ENV:-not set}"

# Package Manager Commands
pnpm-update:  ## Update pnpm to latest version
	npm install -g pnpm@latest

pnpm-check:  ## Check pnpm version and configuration
	pnpm --version
	pnpm config list

# CI/CD Commands
ci-install:  ## CI: Install dependencies (frozen lockfile)
	pnpm install --frozen-lockfile

ci-build:  ## CI: Full build pipeline
	pnpm install --frozen-lockfile
	pnpm run lint
	pnpm run format:check
	tsc --noEmit
	pnpm run build

ci-check: ci-install check type-check  ## CI: Run all checks

# Docker Support (for future use)
docker-build:  ## Build Docker image (requires Dockerfile)
	@if [ -f Dockerfile ]; then \
		docker build -t dataops-basecamp-ui:latest .; \
	else \
		echo "Dockerfile not found. Create one to enable Docker builds."; \
	fi

docker-run:  ## Run Docker container (requires built image)
	@if docker images | grep -q dataops-basecamp-ui; then \
		docker run -p 3000:3000 dataops-basecamp-ui:latest; \
	else \
		echo "Docker image not found. Run 'make docker-build' first."; \
	fi

# Development Tools Setup
tools-install:  ## Install global development tools
	@echo "Installing global tools..."
	npm install -g @antfu/ni
	npm install -g pnpm

vscode-setup:  ## Setup VS Code workspace settings
	@if [ ! -d .vscode ]; then mkdir .vscode; fi
	@echo '{"recommendations":["bradlc.vscode-tailwindcss","esbenp.prettier-vscode","dbaeumer.vscode-eslint","ms-vscode.vscode-typescript-next"]}' > .vscode/extensions.json
	@echo "✅ VS Code extensions recommended"

# Performance & Analysis
lighthouse:  ## Run Lighthouse audit (requires Chrome)
	@echo "🔍 Running Lighthouse audit..."
	@if command -v lighthouse >/dev/null 2>&1; then \
		lighthouse http://localhost:4173 --output html --output-path ./lighthouse-report.html; \
	else \
		echo "Install Lighthouse: npm install -g lighthouse"; \
	fi

# Quick Start Guide
quick-start:  ## Quick start guide for new developers
	@echo "🚀 DataOps Basecamp UI - Quick Start Guide"
	@echo ""
	@echo "1. First time setup:"
	@echo "   make install          # Install dependencies"
	@echo "   make env-setup        # Setup environment file"
	@echo ""
	@echo "2. Development:"
	@echo "   make dev              # Start development server (http://localhost:5173)"
	@echo "   make dev-host         # Start with external access"
	@echo ""
	@echo "3. Code quality:"
	@echo "   make format           # Format code"
	@echo "   make lint             # Check linting"
	@echo "   make type-check       # TypeScript check"
	@echo ""
	@echo "4. Build & deploy:"
	@echo "   make build            # Production build"
	@echo "   make preview          # Preview build locally"
	@echo ""
	@echo "5. Maintenance:"
	@echo "   make check-all        # Full verification"
	@echo "   make deps-outdated    # Check dependency updates"

# Debug & Troubleshooting
debug-info:  ## Show debug information
	@echo "🔍 Debug Information:"
	@echo ""
	@echo "📦 Package Manager:"
	@pnpm --version 2>/dev/null || echo "pnpm not found"
	@echo ""
	@echo "📂 Project Structure:"
	@echo "  ├── src/              # Source code"
	@echo "  ├── public/           # Static assets"
	@echo "  ├── dist/             # Build output"
	@echo "  └── node_modules/     # Dependencies"
	@echo ""
	@echo "🌐 Development URLs:"
	@echo "  • Dev Server:    http://localhost:5173"
	@echo "  • Preview:       http://localhost:4173"
	@echo ""
	@echo "🔧 Main Technologies:"
	@echo "  • React 19"
	@echo "  • TypeScript"
	@echo "  • Vite"
	@echo "  • Tailwind CSS"
	@echo "  • TanStack Router & Query"
	@echo "  • Radix UI Components"

logs:  ## Show recent development logs
	@echo "📝 Recent development server logs:"
	@echo "(Start dev server with 'make dev' to see live logs)"

status:  ## Show project status and endpoints
	@echo "📊 Project Status:"
	@echo ""
	@if [ -d dist ]; then \
		echo "✅ Production build available"; \
	else \
		echo "❌ No production build found"; \
	fi
	@if [ -d node_modules ]; then \
		echo "✅ Dependencies installed"; \
	else \
		echo "❌ Dependencies not installed"; \
	fi
	@echo ""
	@echo "🌐 Available endpoints when running:"
	@echo "  • Development: http://localhost:5173"
	@echo "  • Preview:     http://localhost:4173"