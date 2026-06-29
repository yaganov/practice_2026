.PHONY: help up down build logs ps test reset

help:
	@echo "Targets:"
	@echo "  up     - start the full stack (build + detach)"
	@echo "  down   - stop the stack (keep data)"
	@echo "  build  - rebuild the application image"
	@echo "  logs   - follow app and postgres logs"
	@echo "  ps     - show service status"
	@echo "  test   - run Maven tests inside Docker build"
	@echo "  reset  - stop stack and remove volumes (destructive)"

up:
	docker compose up --build -d

down:
	docker compose down

build:
	docker compose build app

logs:
	docker compose logs -f app postgres

ps:
	docker compose ps

test:
	docker build --target build -f soap-messenger-server/Dockerfile soap-messenger-server

reset:
	@echo "WARNING: removes containers and volumes (all database data will be lost)"
	docker compose down -v
