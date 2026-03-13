# =============================================================================
# Makefile — FoodOrder backend developer commands
#
# Usage
# ──────
#   make             → print this help message
#   make <target>    → run a specific target
#
# Prerequisites
# ──────────────
#   Docker + Docker Compose  required for container targets
#   Java 17 + mvnw           required for local Maven targets
#   openssl                  required for the jwt-secret target
#
# Secrets
# ────────
#   All secrets are loaded from .env (git-ignored).
#   Run `make setup` on first checkout to create .env from .env.example.
# =============================================================================

# ── Load .env if present ──────────────────────────────────────────────────────
# Variables from .env are exported into the environment of every recipe.
# This lets Make targets use $(DB_USERNAME), $(JWT_SECRET), etc. directly.
# Suppress errors if .env is absent — not every environment uses a .env file.
-include .env
export

# ── Convenience variables ─────────────────────────────────────────────────────
COMPOSE         := docker compose
COMPOSE_PROD    := docker compose -f docker-compose.yml
APP_CONTAINER   := foodorder-app
DB_CONTAINER    := foodorder-postgres
DB_NAME         ?= foodorder
MAVEN           := ./mvnw
MAVEN_FLAGS     := -B --no-transfer-progress

# Inject build metadata into OCI image labels at build time.
# Falls back to 'unknown' if git or date are unavailable.
GIT_SHA         := $(shell git rev-parse --short HEAD 2>/dev/null || echo "unknown")
BUILD_DATE      := $(shell date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo "unknown")

# ── Guard: require .env ───────────────────────────────────────────────────────
# Targets that talk to a running container or database need secrets from .env.
# Warn early rather than fail deep inside a recipe with a cryptic error.
.PHONY: _require-env
_require-env:
	@if [ ! -f .env ]; then \
	  echo ""; \
	  echo "  ERROR: .env not found."; \
	  echo "  Run:  make setup"; \
	  echo ""; \
	  exit 1; \
	fi

# ── Default target ────────────────────────────────────────────────────────────
.DEFAULT_GOAL := help

# =============================================================================
# HELP
# =============================================================================

.PHONY: help
help: ## Show this help message
	@echo ""
	@echo "  foodorder-backend — available targets"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'
	@echo ""

# =============================================================================
# FIRST-TIME SETUP
# =============================================================================

.PHONY: setup
setup: ## First-time setup: copy .env.example → .env, generate JWT secret
	@if [ -f .env ]; then \
	  echo "  .env already exists — skipping copy"; \
	else \
	  cp .env.example .env; \
	  echo "  .env created from .env.example"; \
	fi
	@echo "  Next steps:"
	@echo "    1. Edit .env — fill in DB_PASSWORD and JWT_SECRET"
	@echo "    2. Generate a JWT secret:  make jwt-secret"
	@echo "    3. Start services:         make up"
	@echo ""

.PHONY: jwt-secret
jwt-secret: ## Generate a cryptographically secure JWT secret (paste into .env)
	@echo ""
	@echo "  Copy this value into JWT_SECRET in your .env file:"
	@echo ""
	@printf "  JWT_SECRET="; openssl rand -base64 32
	@echo ""

# =============================================================================
# DOCKER — local development (uses docker-compose.yml + override automatically)
# =============================================================================

.PHONY: up
up: _require-env ## Build images and start all services in the background
	GIT_SHA=$(GIT_SHA) BUILD_DATE=$(BUILD_DATE) \
	  $(COMPOSE) up --build --detach
	@echo ""
	@echo "  Services started. Useful follow-up commands:"
	@echo "    make ps        — show container status"
	@echo "    make logs      — tail all logs"
	@echo "    make logs-app  — tail app logs only"
	@echo ""

.PHONY: up-prod
up-prod: _require-env ## Start WITHOUT the override file (production-parity, no host ports)
	@echo "  Starting in production-parity mode (no dev overrides, no host ports)..."
	GIT_SHA=$(GIT_SHA) BUILD_DATE=$(BUILD_DATE) \
	  $(COMPOSE_PROD) up --build --detach

.PHONY: down
down: ## Stop and remove containers and networks (preserves volumes)
	$(COMPOSE) down
	@echo "  Containers stopped. Data volume preserved."
	@echo "  To also remove the database volume: make clean"

.PHONY: down-v
down-v: ## Stop containers AND remove all volumes (DESTROYS database data)
	@echo ""
	@echo "  WARNING: This will destroy the postgres-data volume and all DB data."
	@printf "  Continue? [y/N] "; read ans; [ "$${ans:-N}" = "y" ] || exit 0
	$(COMPOSE) down --volumes
	@echo "  Containers and volumes removed."

.PHONY: restart
restart: ## Restart the app container only (fast — skips rebuild and postgres)
	$(COMPOSE) restart app

.PHONY: build
build: ## Build the Docker image (no cache bust)
	GIT_SHA=$(GIT_SHA) BUILD_DATE=$(BUILD_DATE) \
	  $(COMPOSE) build app

.PHONY: build-nc
build-nc: ## Build the Docker image with --no-cache (forces full rebuild)
	GIT_SHA=$(GIT_SHA) BUILD_DATE=$(BUILD_DATE) \
	  $(COMPOSE) build --no-cache app

# =============================================================================
# STATUS & LOGS
# =============================================================================

.PHONY: ps
ps: ## Show status of all running containers
	$(COMPOSE) ps

.PHONY: logs
logs: ## Tail logs from all services (Ctrl-C to stop)
	$(COMPOSE) logs --follow

.PHONY: logs-app
logs-app: ## Tail app service logs only
	$(COMPOSE) logs --follow app

.PHONY: logs-db
logs-db: ## Tail postgres logs only
	$(COMPOSE) logs --follow postgres

# =============================================================================
# SHELL ACCESS
# =============================================================================

.PHONY: shell
shell: ## Open a shell inside the running app container (ash — Alpine default)
	$(COMPOSE) exec app sh

.PHONY: shell-root
shell-root: ## Open a root shell in the app container (for debugging only)
	$(COMPOSE) exec --user root app sh

.PHONY: db-shell
db-shell: _require-env ## Open psql inside the running postgres container
	$(COMPOSE) exec postgres psql \
	  -U $${DB_USERNAME:-foodorder} \
	  -d $${POSTGRES_DB:-$(DB_NAME)}

# =============================================================================
# DATABASE / FLYWAY
# =============================================================================

.PHONY: migrate-info
migrate-info: _require-env ## Show current Flyway migration status
	$(MAVEN) $(MAVEN_FLAGS) flyway:info \
	  -Dflyway.url=$${DB_URL:-jdbc:postgresql://localhost:5432/$(DB_NAME)} \
	  -Dflyway.user=$${DB_USERNAME} \
	  -Dflyway.password=$${DB_PASSWORD}

.PHONY: migrate-repair
migrate-repair: _require-env ## Repair failed Flyway migrations (use with caution)
	@echo "  Repairing Flyway migration checksums..."
	$(MAVEN) $(MAVEN_FLAGS) flyway:repair \
	  -Dflyway.url=$${DB_URL:-jdbc:postgresql://localhost:5432/$(DB_NAME)} \
	  -Dflyway.user=$${DB_USERNAME} \
	  -Dflyway.password=$${DB_PASSWORD}

.PHONY: migrate-validate
migrate-validate: _require-env ## Validate that applied migrations match SQL files on disk
	$(MAVEN) $(MAVEN_FLAGS) flyway:validate \
	  -Dflyway.url=$${DB_URL:-jdbc:postgresql://localhost:5432/$(DB_NAME)} \
	  -Dflyway.user=$${DB_USERNAME} \
	  -Dflyway.password=$${DB_PASSWORD}

# =============================================================================
# MAVEN — local build (without Docker)
# =============================================================================

.PHONY: package
package: ## Compile, test, and package the fat JAR (./target/*.jar)
	$(MAVEN) $(MAVEN_FLAGS) package

.PHONY: package-skip-tests
package-skip-tests: ## Package the fat JAR skipping tests (faster, CI use)
	$(MAVEN) $(MAVEN_FLAGS) package -DskipTests

.PHONY: test
test: ## Run the full test suite
	$(MAVEN) $(MAVEN_FLAGS) test

.PHONY: test-it
test-it: ## Run integration tests only (requires -Pintegration-test profile)
	$(MAVEN) $(MAVEN_FLAGS) verify -Pintegration-test

.PHONY: clean
clean: ## Remove Maven build output (target/) — does NOT remove Docker volumes
	$(MAVEN) $(MAVEN_FLAGS) clean

.PHONY: clean-all
clean-all: clean ## Remove Maven output AND stop containers AND remove volumes
	$(MAKE) down-v

.PHONY: run-dev
run-dev: _require-env ## Run app locally with dev profile (needs Postgres on localhost:5432)
	$(MAVEN) $(MAVEN_FLAGS) spring-boot:run \
	  -Dspring-boot.run.profiles=dev

# =============================================================================
# CODE QUALITY
# =============================================================================

.PHONY: lint
lint: ## Run Checkstyle static analysis (if configured in pom.xml)
	$(MAVEN) $(MAVEN_FLAGS) checkstyle:check 2>/dev/null \
	  || echo "  Checkstyle not configured in pom.xml — skipping"

.PHONY: verify
verify: ## Run full Maven verify lifecycle (compile, test, integration-test, checkstyle)
	$(MAVEN) $(MAVEN_FLAGS) verify

# =============================================================================
# UTILITIES
# =============================================================================

.PHONY: health
health: ## Check the app health endpoint
	@curl -sf http://localhost:8080/api/v1/health \
	  | python3 -m json.tool 2>/dev/null \
	  || echo "  App is not reachable on localhost:8080 — is it running? (make up)"

.PHONY: image-size
image-size: ## Show the size of the built Docker image
	@docker images foodorder-foodorder-app --format \
	  "Image: {{.Repository}}:{{.Tag}}  Size: {{.Size}}  Created: {{.CreatedAt}}"

.PHONY: env-check
env-check: ## Print all resolved environment variables (masks secret values)
	@echo ""
	@echo "  Resolved environment (secrets masked):"
	@echo "    DB_USERNAME           = $${DB_USERNAME:-<not set>}"
	@echo "    DB_PASSWORD           = $$([ -n "$${DB_PASSWORD}" ] && echo '***' || echo '<not set>')"
	@echo "    POSTGRES_DB           = $${POSTGRES_DB:-foodorder}"
	@echo "    JWT_SECRET            = $$([ -n "$${JWT_SECRET}" ] && echo '***' || echo '<not set>')"
	@echo "    JWT_EXPIRATION_MS     = $${JWT_EXPIRATION_MS:-3600000}"
	@echo "    CORS_ALLOWED_ORIGINS  = $${CORS_ALLOWED_ORIGINS:-<not set>}"
	@echo "    SPRING_PROFILES_ACTIVE= $${SPRING_PROFILES_ACTIVE:-dev}"
	@echo ""