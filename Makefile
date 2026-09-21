# Common tasks. Run `make help` to list them.
.PHONY: help build verify run down clean

help: ## Show this help
	@grep -E '^[a-z]+:.*##' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-8s %s\n", $$1, $$2}'
build: ## Compile without running tests
	./mvnw -B -q -DskipTests package
verify: ## Build and run every test (needs Docker for Testcontainers)
	./mvnw -B verify
run: ## Start the application and PostgreSQL with Docker Compose
	docker compose up --build
down: ## Stop the stack and delete its database volume
	docker compose down -v
clean: ## Remove build output
	./mvnw -B -q clean
