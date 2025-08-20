# Quickstart

This guide shows how to run the Cashu Vault service either with Docker Compose or directly from source.

## Prerequisites

- Git
- Docker and Docker Compose
- Java 21
- Maven 3.8+

## Run with Docker Compose

1. Clone the repository and navigate to the project root:
   ```bash
   git clone https://github.com/[your-org]/cashu-vault.git
   cd cashu-vault
   ```
2. Build and start the containers:
   ```bash
   docker-compose up --build
   ```
3. When the logs show the service has started, visit `http://localhost:3333`.
   The PostgreSQL database listens on port `5432`.

Stop the environment with `Ctrl+C` and remove the containers with:
```bash
docker-compose down
```

## Run from source

1. Build the project:
   ```bash
   mvn package
   ```
2. Start the service:
   ```bash
   java -jar cashu-vault-jpa/target/cashu-vault-jpa-*.jar
   # or
   ./cashu-vault-jpa/mvnw spring-boot:run
   ```
3. The service listens on `http://localhost:3333`.

Shut down the service with `Ctrl+C`.
