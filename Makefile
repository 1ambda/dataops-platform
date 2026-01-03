# ==============================================================================
# DataOps Platform - Enhanced Makefile with BuildKit Support
# ==============================================================================
# Optimized for developer experience with Docker BuildKit cache mounts
# Achieves 30-60% faster rebuilds and 85-95% faster no-change builds
# ==============================================================================

# Colors for better terminal output
BOLD := \033[1m
RESET := \033[0m
GREEN := \033[32m
YELLOW := \033[33m
BLUE := \033[34m
RED := \033[31m
CYAN := \033[36m

# Project metadata
PROJECT_NAME := dataops-platform
TAG := MAKE
TIMESTAMP := $(shell date '+%H:%M:%S')

# Docker configuration
DOCKER_BUILDKIT := 1
COMPOSE_DOCKER_CLI_BUILD := 1
COMPOSE_INFRA_FILES := -f docker-compose.yaml
COMPOSE_ALL_FILES := -f docker-compose.all.yaml
COMPOSE_CMD := DOCKER_BUILDKIT=$(DOCKER_BUILDKIT) COMPOSE_DOCKER_CLI_BUILD=$(COMPOSE_DOCKER_CLI_BUILD) docker compose $(COMPOSE_INFRA_FILES)
COMPOSE_CMD_ALL := DOCKER_BUILDKIT=$(DOCKER_BUILDKIT) COMPOSE_DOCKER_CLI_BUILD=$(COMPOSE_DOCKER_CLI_BUILD) docker compose $(COMPOSE_ALL_FILES)

# Service names
SERVICE_SERVER := basecamp-server
SERVICE_PARSER := basecamp-parser
SERVICE_UI := basecamp-ui
SERVICES := $(SERVICE_SERVER) $(SERVICE_PARSER) $(SERVICE_UI)

# Infrastructure services
INFRA_MYSQL := mysql
INFRA_REDIS := redis
INFRA_POSTGRES := postgres
INFRA_KEYCLOAK := keycloak
INFRA_SERVICES := $(INFRA_MYSQL) $(INFRA_REDIS) $(INFRA_POSTGRES) $(INFRA_KEYCLOAK)

# Port configuration (infrastructure mode: docker-compose.yaml)
PORT_MYSQL := 3306
PORT_REDIS := 6379
PORT_POSTGRES := 5432
PORT_KEYCLOAK := 8080

# Port configuration (full stack mode: docker-compose.all.yaml)
PORT_SERVER := 8081
PORT_PARSER := 5000
PORT_UI := 3000

# ==============================================================================
# Help & Documentation
# ==============================================================================

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help message
	@echo ""
	@echo "$(BOLD)$(CYAN)╔════════════════════════════════════════════════════════════════╗$(RESET)"
	@echo "$(BOLD)$(CYAN)║       DataOps Platform - Development Makefile                 ║$(RESET)"
	@echo "$(BOLD)$(CYAN)║       BuildKit-Optimized Docker Workflows                     ║$(RESET)"
	@echo "$(BOLD)$(CYAN)╚════════════════════════════════════════════════════════════════╝$(RESET)"
	@echo ""
	@echo "$(BOLD)$(GREEN)Quick Start Commands:$(RESET)"
	@echo "  $(CYAN)make setup$(RESET)          - Complete environment setup (first time)"
	@echo "  $(CYAN)make dev$(RESET)            - Start all services in development mode"
	@echo "  $(CYAN)make stop$(RESET)           - Stop all running services"
	@echo "  $(CYAN)make clean$(RESET)          - Clean up containers and volumes"
	@echo ""
	@echo "$(BOLD)$(GREEN)Available Targets:$(RESET)"
	@grep -E '^[a-zA-Z_.-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(CYAN)%-20s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(BOLD)$(YELLOW)Infrastructure Ports (docker-compose.yaml):$(RESET)"
	@echo "  mysql              → localhost:$(PORT_MYSQL)"
	@echo "  redis              → localhost:$(PORT_REDIS)"
	@echo "  postgres           → localhost:$(PORT_POSTGRES)"
	@echo "  keycloak           → http://localhost:$(PORT_KEYCLOAK)"
	@echo ""
	@echo "$(BOLD)$(YELLOW)Full Stack Ports (docker-compose.all.yaml):$(RESET)"
	@echo "  basecamp-server    → http://localhost:$(PORT_SERVER)"
	@echo "  basecamp-parser    → http://localhost:$(PORT_PARSER)"
	@echo "  basecamp-ui        → http://localhost:$(PORT_UI)"
	@echo "  + all infrastructure services above"
	@echo ""

# ==============================================================================
# Environment Setup & Validation
# ==============================================================================

.PHONY: setup
setup: check-deps init info ## Complete first-time setup (check dependencies, initialize)
	@echo ""
	@echo "$(BOLD)$(GREEN)✓ Setup complete! Run 'make dev' to start services$(RESET)"
	@echo ""

.PHONY: check-deps
check-deps: ## Validate required dependencies are installed
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Checking dependencies...$(RESET)"
	@command -v docker >/dev/null 2>&1 || { echo "$(RED)✗ Docker not found. Please install Docker Desktop$(RESET)"; exit 1; }
	@command -v docker compose >/dev/null 2>&1 || { echo "$(RED)✗ Docker Compose not found$(RESET)"; exit 1; }
	@echo "$(GREEN)✓ Docker $(shell docker --version)$(RESET)"
	@echo "$(GREEN)✓ Docker Compose $(shell docker compose version)$(RESET)"
	@if docker buildx version >/dev/null 2>&1; then echo "$(GREEN)✓ BuildKit support available$(RESET)"; else echo "$(YELLOW)⚠ BuildKit not available (slower builds)$(RESET)"; fi
	@echo ""

.PHONY: init
init: ## Initialize project configuration and directories
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Initializing project...$(RESET)"
	@mkdir -p docker/volumes/mysql docker/volumes/redis
	@echo "$(GREEN)✓ Created volume directories$(RESET)"
	@echo ""

.PHONY: info
info: ## Display project and environment information
	@echo ""
	@echo "$(BOLD)$(CYAN)Project Information:$(RESET)"
	@echo "  Name:              $(PROJECT_NAME)"
	@echo "  Compose Modes:     Infrastructure (docker-compose.yaml) | Full Stack (docker-compose.all.yaml)"
	@echo "  BuildKit Enabled:  $(DOCKER_BUILDKIT)"
	@echo "  Working Directory: $(shell pwd)"
	@echo ""
	@echo "$(BOLD)$(CYAN)Services:$(RESET)"
	@echo "  Application:       $(SERVICES)"
	@echo "  Infrastructure:    $(INFRA_SERVICES)"
	@echo ""
	@echo "$(BOLD)$(CYAN)Docker Compose Modes:$(RESET)"
	@echo "  docker-compose.yaml      → Infrastructure only (MySQL, Redis, PostgreSQL, Keycloak)"
	@echo "  docker-compose.all.yaml  → Full stack (Infrastructure + Application services)"
	@echo ""

# ==============================================================================
# Development Workflow
# ==============================================================================

.PHONY: dev
dev: ## Start infrastructure services only (MySQL, Redis, PostgreSQL, Keycloak)
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Starting infrastructure services...$(RESET)"
	@$(COMPOSE_CMD) up -d
	@echo ""
	@echo "$(GREEN)✓ Infrastructure services started! Waiting for health checks...$(RESET)"
	@sleep 5
	@make status
	@echo ""
	@echo "$(BOLD)$(GREEN)Infrastructure ready for local development!$(RESET)"
	@echo "  MySQL:     localhost:$(PORT_MYSQL)"
	@echo "  Redis:     localhost:$(PORT_REDIS)"
	@echo "  Postgres:  localhost:$(PORT_POSTGRES)"
	@echo "  Keycloak:  http://localhost:$(PORT_KEYCLOAK)"
	@echo ""
	@echo "$(BOLD)$(YELLOW)Note: Run application services locally or use 'make dev-all' for full stack$(RESET)"
	@echo ""

.PHONY: dev-all
dev-all: ## Start full stack (infrastructure + all application services via Docker)
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Starting full stack environment...$(RESET)"
	@$(COMPOSE_CMD_ALL) up -d
	@echo ""
	@echo "$(GREEN)✓ All services started! Waiting for health checks...$(RESET)"
	@sleep 5
	@make status
	@echo ""
	@echo "$(BOLD)$(GREEN)Full stack ready!$(RESET)"
	@echo "  UI:        http://localhost:$(PORT_UI)"
	@echo "  Server:    http://localhost:$(PORT_SERVER)/api/health"
	@echo "  Parser:    http://localhost:$(PORT_PARSER)/health"
	@echo "  Keycloak:  http://localhost:$(PORT_KEYCLOAK)"
	@echo ""

.PHONY: dev-logs
dev-logs: ## Start infrastructure services with attached logs
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Starting infrastructure with logs attached...$(RESET)"
	@$(COMPOSE_CMD) up

.PHONY: dev-all-logs
dev-all-logs: ## Start full stack with attached logs
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Starting full stack with logs attached...$(RESET)"
	@$(COMPOSE_CMD_ALL) up

.PHONY: stop
stop: ## Stop all running services (keeps containers)
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Stopping services...$(RESET)"
	@$(COMPOSE_CMD_ALL) stop
	@echo "$(GREEN)✓ Services stopped$(RESET)"
	@echo ""

.PHONY: restart
restart: stop dev ## Restart all services

.PHONY: down
down: ## Stop and remove all containers (keeps volumes)
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Removing containers...$(RESET)"
	@$(COMPOSE_CMD_ALL) down
	@echo "$(GREEN)✓ Containers removed$(RESET)"
	@echo ""

# ==============================================================================
# Service-Specific Commands
# ==============================================================================

.PHONY: build
build: ## Build all service images with BuildKit cache
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Building all images with BuildKit...$(RESET)"
	@DOCKER_BUILDKIT=$(DOCKER_BUILDKIT) $(COMPOSE_CMD_ALL) build --progress=plain
	@echo "$(GREEN)✓ Build complete$(RESET)"
	@echo ""

.PHONY: build-server
build-server: ## Build basecamp-server image only
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Building $(SERVICE_SERVER)...$(RESET)"
	@DOCKER_BUILDKIT=$(DOCKER_BUILDKIT) $(COMPOSE_CMD) build $(SERVICE_SERVER)
	@echo "$(GREEN)✓ $(SERVICE_SERVER) built$(RESET)"
	@echo ""

.PHONY: build-parser
build-parser: ## Build basecamp-parser image only
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Building $(SERVICE_PARSER)...$(RESET)"
	@DOCKER_BUILDKIT=$(DOCKER_BUILDKIT) $(COMPOSE_CMD) build $(SERVICE_PARSER)
	@echo "$(GREEN)✓ $(SERVICE_PARSER) built$(RESET)"
	@echo ""

.PHONY: build-ui
build-ui: ## Build basecamp-ui image only
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Building $(SERVICE_UI)...$(RESET)"
	@DOCKER_BUILDKIT=$(DOCKER_BUILDKIT) $(COMPOSE_CMD) build $(SERVICE_UI)
	@echo "$(GREEN)✓ $(SERVICE_UI) built$(RESET)"
	@echo ""

.PHONY: rebuild
rebuild: clean-images build ## Clean and rebuild all images from scratch

.PHONY: up-server
up-server: ## Start basecamp-server only
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Starting $(SERVICE_SERVER)...$(RESET)"
	@$(COMPOSE_CMD) up -d $(SERVICE_SERVER)
	@echo "$(GREEN)✓ $(SERVICE_SERVER) started on port $(PORT_SERVER)$(RESET)"
	@echo ""

.PHONY: up-parser
up-parser: ## Start basecamp-parser only
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Starting $(SERVICE_PARSER)...$(RESET)"
	@$(COMPOSE_CMD) up -d $(SERVICE_PARSER)
	@echo "$(GREEN)✓ $(SERVICE_PARSER) started on port $(PORT_PARSER)$(RESET)"
	@echo ""

.PHONY: up-ui
up-ui: ## Start basecamp-ui only
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Starting $(SERVICE_UI)...$(RESET)"
	@$(COMPOSE_CMD) up -d $(SERVICE_UI)
	@echo "$(GREEN)✓ $(SERVICE_UI) started on port $(PORT_UI)$(RESET)"
	@echo ""

# ==============================================================================
# Logs & Monitoring
# ==============================================================================

.PHONY: logs
logs: ## Show logs from all services (follow mode)
	@$(COMPOSE_CMD_ALL) logs -f

.PHONY: logs-server
logs-server: ## Show basecamp-server logs
	@$(COMPOSE_CMD) logs -f $(SERVICE_SERVER)

.PHONY: logs-parser
logs-parser: ## Show basecamp-parser logs
	@$(COMPOSE_CMD) logs -f $(SERVICE_PARSER)

.PHONY: logs-ui
logs-ui: ## Show basecamp-ui logs
	@$(COMPOSE_CMD) logs -f $(SERVICE_UI)

.PHONY: logs-mysql
logs-mysql: ## Show MySQL logs
	@$(COMPOSE_CMD) logs -f $(INFRA_MYSQL)

.PHONY: logs-redis
logs-redis: ## Show Redis logs
	@$(COMPOSE_CMD) logs -f $(INFRA_REDIS)

.PHONY: logs-postgres
logs-postgres: ## Show PostgreSQL logs
	@$(COMPOSE_CMD) logs -f $(INFRA_POSTGRES)

.PHONY: logs-keycloak
logs-keycloak: ## Show Keycloak logs
	@$(COMPOSE_CMD) logs -f $(INFRA_KEYCLOAK)

.PHONY: status
status: ## Show status of all containers
	@echo ""
	@echo "$(BOLD)$(CYAN)Container Status:$(RESET)"
	@$(COMPOSE_CMD_ALL) ps
	@echo ""

.PHONY: health
health: ## Check health status of all services
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Checking service health...$(RESET)"
	@echo ""
	@echo "$(BOLD)$(CYAN)Infrastructure Services:$(RESET)"
	@docker exec dataops-mysql mysqladmin ping -h localhost 2>/dev/null >/dev/null && echo "  $(GREEN)✓ MySQL OK$(RESET)" || echo "  $(RED)✗ MySQL unavailable$(RESET)"
	@docker exec dataops-redis redis-cli ping 2>/dev/null >/dev/null && echo "  $(GREEN)✓ Redis OK$(RESET)" || echo "  $(RED)✗ Redis unavailable$(RESET)"
	@docker exec ops-postgres pg_isready -U postgres 2>/dev/null >/dev/null && echo "  $(GREEN)✓ PostgreSQL OK$(RESET)" || echo "  $(RED)✗ PostgreSQL unavailable$(RESET)"
	@curl -f -s http://localhost:$(PORT_KEYCLOAK)/health 2>/dev/null >/dev/null && echo "  $(GREEN)✓ Keycloak OK$(RESET)" || echo "  $(YELLOW)⚠ Keycloak unavailable (check http://localhost:$(PORT_KEYCLOAK))$(RESET)"
	@echo ""
	@echo "$(BOLD)$(CYAN)Application Services:$(RESET)"
	@curl -f -s http://localhost:$(PORT_SERVER)/api/health 2>/dev/null && echo "  $(GREEN)✓ Server OK$(RESET)" || echo "  $(YELLOW)⚠ Server unavailable (requires docker-compose.all.yaml)$(RESET)"
	@curl -f -s http://localhost:$(PORT_PARSER)/health 2>/dev/null && echo "  $(GREEN)✓ Parser OK$(RESET)" || echo "  $(YELLOW)⚠ Parser unavailable (requires docker-compose.all.yaml)$(RESET)"
	@curl -f -s http://localhost:$(PORT_UI)/ 2>/dev/null >/dev/null && echo "  $(GREEN)✓ UI OK$(RESET)" || echo "  $(YELLOW)⚠ UI unavailable (requires docker-compose.all.yaml)$(RESET)"
	@echo ""

# ==============================================================================
# Database Management
# ==============================================================================

.PHONY: db-shell
db-shell: ## Open MySQL shell
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Opening MySQL shell...$(RESET)"
	@docker exec -it dataops-mysql mysql -u dataops_user -pdataops_password dataops

.PHONY: db-reset
db-reset: ## Reset database (WARNING: destroys all data)
	@echo ""
	@echo "$(BOLD)$(RED)⚠ WARNING: This will delete all database data!$(RESET)"
	@read -p "Are you sure? (yes/no): " confirm && [ "$$confirm" = "yes" ] || exit 1
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Resetting database...$(RESET)"
	@$(COMPOSE_CMD) stop $(INFRA_MYSQL)
	@docker volume rm dataops-platform_mysql_data 2>/dev/null || true
	@$(COMPOSE_CMD) up -d $(INFRA_MYSQL)
	@sleep 5
	@echo "$(GREEN)✓ Database reset complete$(RESET)"
	@echo ""

.PHONY: redis-cli
redis-cli: ## Open Redis CLI
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Opening Redis CLI...$(RESET)"
	@docker exec -it dataops-redis redis-cli

.PHONY: redis-flush
redis-flush: ## Flush all Redis cache
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Flushing Redis cache...$(RESET)"
	@docker exec -it dataops-redis redis-cli FLUSHALL
	@echo "$(GREEN)✓ Redis cache flushed$(RESET)"
	@echo ""

# ==============================================================================
# Cleanup & Maintenance
# ==============================================================================

.PHONY: clean
clean: ## Stop and remove containers, networks (keeps volumes and images)
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Cleaning up containers and networks...$(RESET)"
	@$(COMPOSE_CMD_ALL) down
	@echo "$(GREEN)✓ Cleanup complete$(RESET)"
	@echo ""

.PHONY: clean-volumes
clean-volumes: ## Remove all volumes (WARNING: destroys all data)
	@echo ""
	@echo "$(BOLD)$(RED)⚠ WARNING: This will delete all persistent data!$(RESET)"
	@read -p "Are you sure? (yes/no): " confirm && [ "$$confirm" = "yes" ] || exit 1
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Removing volumes...$(RESET)"
	@$(COMPOSE_CMD_ALL) down -v
	@docker volume prune -f
	@echo "$(GREEN)✓ Volumes removed$(RESET)"
	@echo ""

.PHONY: clean-images
clean-images: ## Remove all project images
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Removing project images...$(RESET)"
	@docker rmi -f $(SERVICE_SERVER):latest $(SERVICE_PARSER):latest $(SERVICE_UI):latest 2>/dev/null || true
	@echo "$(GREEN)✓ Images removed$(RESET)"
	@echo ""

.PHONY: clean-all
clean-all: clean-volumes clean-images ## Nuclear option: remove everything (containers, volumes, images)
	@echo ""
	@echo "$(BOLD)$(GREEN)✓ Complete cleanup finished$(RESET)"
	@echo ""

.PHONY: prune
prune: ## Prune Docker system (unused containers, networks, images)
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Pruning Docker system...$(RESET)"
	@docker container prune -f
	@docker network prune -f
	@docker volume prune -f
	@docker image prune -f
	@echo "$(GREEN)✓ System pruned$(RESET)"
	@echo ""

# ==============================================================================
# Development Utilities
# ==============================================================================

.PHONY: open
open: ## Open all service UIs in browser
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Opening service UIs...$(RESET)"
	@open http://localhost:$(PORT_UI) 2>/dev/null || xdg-open http://localhost:$(PORT_UI) 2>/dev/null || echo "$(YELLOW)Please open http://localhost:$(PORT_UI) manually$(RESET)"
	@open http://localhost:$(PORT_SERVER)/api/health 2>/dev/null || xdg-open http://localhost:$(PORT_SERVER)/api/health 2>/dev/null || true
	@echo ""

.PHONY: shell-server
shell-server: ## Open shell in basecamp-server container
	@docker exec -it basecamp-server sh

.PHONY: shell-parser
shell-parser: ## Open shell in basecamp-parser container
	@docker exec -it basecamp-parser sh

.PHONY: shell-ui
shell-ui: ## Open shell in basecamp-ui container
	@docker exec -it basecamp-ui sh

.PHONY: inspect
inspect: ## Show detailed container inspection
	@echo ""
	@echo "$(BOLD)$(CYAN)Container Details:$(RESET)"
	@docker inspect basecamp-server basecamp-parser basecamp-ui 2>/dev/null | grep -A 5 "State\|NetworkSettings" || echo "$(YELLOW)No containers running$(RESET)"
	@echo ""

# ==============================================================================
# CI/CD & Testing
# ==============================================================================

.PHONY: ci-build
ci-build: ## CI build simulation (no cache, production-like)
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - CI build mode (no cache)...$(RESET)"
	@DOCKER_BUILDKIT=$(DOCKER_BUILDKIT) $(COMPOSE_CMD) build --no-cache --pull
	@echo "$(GREEN)✓ CI build complete$(RESET)"
	@echo ""

.PHONY: validate
validate: check-deps ## Validate Docker Compose configuration
	@echo ""
	@echo "$(BOLD)[$(TAG)] ($(TIMESTAMP)) - Validating configuration...$(RESET)"
	@$(COMPOSE_CMD) config --quiet && echo "$(GREEN)✓ Configuration valid$(RESET)" || echo "$(RED)✗ Configuration invalid$(RESET)"
	@echo ""

# ==============================================================================
# Performance & Optimization
# ==============================================================================

.PHONY: stats
stats: ## Show real-time container resource usage
	@docker stats basecamp-server basecamp-parser basecamp-ui dataops-mysql dataops-redis

.PHONY: disk-usage
disk-usage: ## Show Docker disk usage
	@echo ""
	@echo "$(BOLD)$(CYAN)Docker Disk Usage:$(RESET)"
	@docker system df
	@echo ""

.PHONY: cache-info
cache-info: ## Show BuildKit cache information
	@echo ""
	@echo "$(BOLD)$(CYAN)BuildKit Cache Status:$(RESET)"
	@docker buildx du 2>/dev/null || echo "$(YELLOW)BuildKit cache info not available$(RESET)"
	@echo ""

# ==============================================================================
# Aliases & Shortcuts
# ==============================================================================

.PHONY: start
start: dev ## Alias for 'dev'

.PHONY: up
up: dev ## Alias for 'dev'

.PHONY: ps
ps: status ## Alias for 'status'

.PHONY: tail
tail: logs ## Alias for 'logs'

# ==============================================================================
# Project-Specific Tasks
# ==============================================================================

.PHONY: compose
compose: dev-logs ## Legacy: Start with logs (matches old Makefile behavior)

.PHONY: compose.clean
compose.clean: clean prune ## Legacy: Clean containers and volumes (matches old Makefile behavior)

# ==============================================================================
# Serena Symbol Update System
# ==============================================================================

.PHONY: serena-help
serena-help: ## Show Serena symbol update system help
	@echo ""
	@echo "$(BOLD)$(CYAN)Serena Symbol Auto-Update System$(RESET)"
	@echo ""
	@echo "$(BOLD)$(GREEN)Quick Commands:$(RESET)"
	@echo "  $(CYAN)make serena-update$(RESET)        - Update all symbols (dry-run first)"
	@echo "  $(CYAN)make serena-update-all$(RESET)    - Force update all symbols and memories"
	@echo "  $(CYAN)make serena-test$(RESET)          - Test symbol update system"
	@echo "  $(CYAN)make serena-fix$(RESET)           - Fix changed files only (Git-based)"
	@echo ""
	@echo "$(BOLD)$(GREEN)Project Updates:$(RESET)"
	@echo "  $(CYAN)make serena-server$(RESET)        - Update basecamp-server symbols, docs & memories"
	@echo "  $(CYAN)make serena-ui$(RESET)            - Update basecamp-ui symbols, docs & memories"
	@echo "  $(CYAN)make serena-parser$(RESET)        - Update basecamp-parser symbols, docs & memories"
	@echo "  $(CYAN)make serena-connect$(RESET)       - Update basecamp-connect symbols, docs & memories"
	@echo "  $(CYAN)make serena-cli$(RESET)           - Update interface-cli symbols, docs & memories"
	@echo ""
	@echo "$(BOLD)$(GREEN)Document Search (94% Token Savings):$(RESET)"
	@echo "  $(CYAN)make doc-search q=\"query\"$(RESET) - Search documentation index"
	@echo "  $(CYAN)make doc-index$(RESET)            - Rebuild document index"
	@echo "  $(CYAN)make doc-index-status$(RESET)     - Check document index status"
	@echo ""

.PHONY: serena-update
serena-update: ## Update Serena symbol cache (with dry-run check)
	@echo "$(BOLD)$(BLUE)[SERENA]$(RESET) Running dry-run first..."
	@python3 scripts/serena/update-symbols.py --all --with-memories --dry-run
	@echo ""
	@echo "$(BOLD)$(YELLOW)Dry-run completed. Run 'make serena-update-all' to execute actual update.$(RESET)"

.PHONY: serena-update-all
serena-update-all: ## Force update all Serena symbols, docs & memories
	@echo "$(BOLD)$(BLUE)[SERENA]$(RESET) Updating all symbols, docs & memories..."
	@python3 scripts/serena/update-symbols.py --all --with-deps --with-docs --with-memories
	@echo "$(BOLD)$(GREEN)[SERENA]$(RESET) Update completed!"

.PHONY: serena-fix
serena-fix: ## Update only changed files (Git-based)
	@echo "$(BOLD)$(BLUE)[SERENA]$(RESET) Updating changed files only..."
	@python3 scripts/serena/update-symbols.py --changed-only --with-memories
	@echo "$(BOLD)$(GREEN)[SERENA]$(RESET) Changed files updated!"

.PHONY: serena-test
serena-test: ## Test Serena symbol update system
	@echo "$(BOLD)$(BLUE)[SERENA]$(RESET) Running system tests..."
	@bash scripts/serena/test-update.sh
	@echo "$(BOLD)$(GREEN)[SERENA]$(RESET) Tests completed!"

.PHONY: serena-status
serena-status: ## Check Serena system status
	@echo "$(BOLD)$(BLUE)[SERENA]$(RESET) System Status Check"
	@echo ""
	@echo "$(BOLD)$(GREEN)Cache Status:$(RESET)"
	@ls -la .serena/cache/*/document_symbols.pkl 2>/dev/null | awk '{print "  " $$9 " (" $$5 " bytes, " $$6 " " $$7 ")"}' || echo "  No cache files found"
	@echo ""
	@echo "$(BOLD)$(GREEN)Memory Status:$(RESET)"
	@ls -1 .serena/memories/ 2>/dev/null | wc -l | awk '{print "  " $$1 " memory files"}' || echo "  No memory files found"
	@echo ""
	@echo "$(BOLD)$(GREEN)Git Hooks Status:$(RESET)"
	@test -x .git/hooks/post-commit && echo "  ✅ post-commit hook active" || echo "  ❌ post-commit hook missing/inactive"
	@test -x .git/hooks/post-merge && echo "  ✅ post-merge hook active" || echo "  ❌ post-merge hook missing/inactive"
	@echo ""

.PHONY: serena-server
serena-server: ## Update basecamp-server symbols, docs & memories
	@echo "$(BOLD)$(BLUE)[SERENA]$(RESET) Updating basecamp-server symbols, docs & memories..."
	@python3 scripts/serena/update-symbols.py --project project-basecamp-server --with-docs --with-memories
	@echo "$(BOLD)$(GREEN)[SERENA]$(RESET) Basecamp-server updated!"

.PHONY: serena-ui
serena-ui: ## Update basecamp-ui symbols, docs & memories
	@echo "$(BOLD)$(BLUE)[SERENA]$(RESET) Updating basecamp-ui symbols, docs & memories..."
	@python3 scripts/serena/update-symbols.py --project project-basecamp-ui --with-docs --with-memories
	@echo "$(BOLD)$(GREEN)[SERENA]$(RESET) Basecamp-ui updated!"

.PHONY: serena-parser
serena-parser: ## Update basecamp-parser symbols, docs & memories
	@echo "$(BOLD)$(BLUE)[SERENA]$(RESET) Updating basecamp-parser symbols, docs & memories..."
	@python3 scripts/serena/update-symbols.py --project project-basecamp-parser --with-docs --with-memories
	@echo "$(BOLD)$(GREEN)[SERENA]$(RESET) Basecamp-parser updated!"

.PHONY: serena-connect
serena-connect: ## Update basecamp-connect symbols, docs & memories
	@echo "$(BOLD)$(BLUE)[SERENA]$(RESET) Updating basecamp-connect symbols, docs & memories..."
	@python3 scripts/serena/update-symbols.py --project project-basecamp-connect --with-docs --with-memories
	@echo "$(BOLD)$(GREEN)[SERENA]$(RESET) Basecamp-connect updated!"

.PHONY: serena-cli
serena-cli: ## Update interface-cli symbols, docs & memories
	@echo "$(BOLD)$(BLUE)[SERENA]$(RESET) Updating interface-cli symbols, docs & memories..."
	@python3 scripts/serena/update-symbols.py --project project-interface-cli --with-docs --with-memories
	@echo "$(BOLD)$(GREEN)[SERENA]$(RESET) Interface-cli updated!"

# ------------------------------------------------------------------------------
# Document Index System (Token-efficient documentation search)
# ------------------------------------------------------------------------------

.PHONY: doc-search
doc-search: ## Search documentation index (usage: make doc-search q="your query")
	@if [ -z "$(q)" ]; then \
		echo "$(BOLD)$(YELLOW)Usage:$(RESET) make doc-search q=\"your search query\""; \
		echo ""; \
		echo "$(BOLD)$(CYAN)Examples:$(RESET)"; \
		echo "  make doc-search q=\"hexagonal architecture\""; \
		echo "  make doc-search q=\"repository pattern\""; \
		echo "  make doc-search q=\"testing\""; \
	else \
		echo "$(BOLD)$(BLUE)[DOC-SEARCH]$(RESET) Searching for: $(q)"; \
		python3 scripts/serena/document_indexer.py --search "$(q)" --max-results 10; \
	fi

.PHONY: doc-index
doc-index: ## Rebuild document search index
	@echo "$(BOLD)$(BLUE)[DOC-INDEX]$(RESET) Rebuilding document search index..."
	@python3 scripts/serena/document_indexer.py --project-root . --rebuild
	@echo "$(BOLD)$(GREEN)[DOC-INDEX]$(RESET) Document index rebuilt!"

.PHONY: doc-index-status
doc-index-status: ## Check document index status
	@echo "$(BOLD)$(BLUE)[DOC-INDEX]$(RESET) Document Index Status"
	@echo ""
	@if [ -f .serena/cache/documents/document_index.json ]; then \
		echo "  $(GREEN)Index file exists$(RESET)"; \
		python3 -c "import json; d=json.load(open('.serena/cache/documents/document_index.json')); m=d['metadata']; print(f\"  Documents: {m['total_documents']}\"); print(f\"  Sections: {m['total_sections']}\"); print(f\"  Tags: {m['total_tags']}\"); print(f\"  Keywords: {m['total_keywords']}\"); print(f\"  Created: {m['created_at']}\")"; \
	else \
		echo "  $(YELLOW)Index not found. Run 'make doc-index' to create.$(RESET)"; \
	fi

# ==============================================================================
# Notes
# ==============================================================================
# BuildKit Cache Performance:
#   - First build: ~3-5 minutes (no cache)
#   - Rebuild with cache: 30-60% faster (~2-3 minutes)
#   - No-change rebuild: 85-95% faster (~10-30 seconds)
#
# Cache Mounts:
#   - Gradle: /workspace/.gradle
#   - uv: /root/.cache/uv
#   - npm: /root/.cache/npm
#   - APT: /var/cache/apt, /var/lib/apt
#
# Layering Strategy:
#   - Dependencies (least changed) → Source code (most changed)
#   - Spring Boot: metadata → loader → lib → classes
#   - All services use tini for proper PID 1 signal handling
# ==============================================================================
