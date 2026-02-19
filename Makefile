# =============================================================================
# Makefile — Common Docker Compose commands
# Usage: make <target>
# =============================================================================

.PHONY: help up up-prod down down-clean build logs ps shell-app shell-db \
        db-dump db-restore check-env

COMPOSE      := docker compose
COMPOSE_PROD := docker compose -f docker-compose.yml

# Default target
help:
	@echo "Available targets:"
	@echo "  make up           Start all services (dev, with override)"
	@echo "  make up-prod      Start all services (prod config only)"
	@echo "  make down         Stop services (preserve volumes)"
	@echo "  make down-clean   Stop services and DELETE volumes (⚠ destroys DB data)"
	@echo "  make build        Rebuild app image"
	@echo "  make logs         Tail logs for all services"
	@echo "  make ps           Show service status"
	@echo "  make shell-app    Open shell in app container"
	@echo "  make shell-db     Open psql in postgres container"
	@echo "  make db-dump      Dump database to ./backups/dump_\$$(date).sql"
	@echo "  make check-env    Verify required .env variables are set"

# ── Development ───────────────────────────────────────────────────────────────
up: check-env
	$(COMPOSE) up --build -d
	@echo "✓ Services started. API: http://localhost:8080  Health: http://localhost:8081/actuator/health"

down:
	$(COMPOSE) down

down-clean:
	@echo "⚠  WARNING: This will delete all database data."
	@read -p "Are you sure? [y/N] " confirm && [ "$$confirm" = "y" ]
	$(COMPOSE) down -v

build:
	$(COMPOSE) build --no-cache app

logs:
	$(COMPOSE) logs -f

ps:
	$(COMPOSE) ps

shell-app:
	$(COMPOSE) exec app sh

shell-db:
	$(COMPOSE) exec postgres psql -U $${POSTGRES_USER} -d $${POSTGRES_DB}

# ── Production ────────────────────────────────────────────────────────────────
up-prod: check-env
	$(COMPOSE_PROD) up --build -d

# ── Database operations ───────────────────────────────────────────────────────
db-dump:
	@mkdir -p backups
	$(COMPOSE) exec postgres pg_dump -U $${POSTGRES_USER} $${POSTGRES_DB} \
		> backups/dump_$$(date +%Y%m%d_%H%M%S).sql
	@echo "✓ Dump saved to backups/"

db-restore:
	@test -n "$(FILE)" || (echo "Usage: make db-restore FILE=backups/dump.sql" && exit 1)
	$(COMPOSE) exec -T postgres psql -U $${POSTGRES_USER} -d $${POSTGRES_DB} < $(FILE)

# ── Validation ────────────────────────────────────────────────────────────────
check-env:
	@test -f .env || (echo "ERROR: .env file not found. Run: cp .env.example .env" && exit 1)
	@grep -q "REPLACE_WITH" .env && \
		(echo "ERROR: .env contains placeholder values. Please fill in real secrets." && exit 1) || true
	@echo "✓ .env file present and looks configured"