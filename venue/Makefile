.PHONY: dev db-start db-stop db-connect build clean redis-connect

db-start:
	docker compose up -d postgres redis
	@echo "Waiting for postgres to be healthy..."
	@until docker exec venue_postgres pg_isready -U venue_user -d venue_db > /dev/null 2>&1; do sleep 1; done
	@echo "PostgreSQL is ready."

db-stop:
	docker compose down

db-connect:
	docker exec -it venue_postgres psql -U venueuser -d venuedb

redis-connect:
	docker exec -it venue_redis redis-cli

build:
	./mvnw clean package -DskipTests

dev: db-start
	./mvnw spring-boot:run

clean:
	docker compose down -v
	./mvnw clean
