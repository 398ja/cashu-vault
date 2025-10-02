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
2. Build the JAR:
   ```bash
   mvn clean package -DskipTests
   ```
3. Build the Docker image:
   ```bash
   cd cashu-vault-jpa
   docker build -t docker.398ja.xyz/cashu-vault-jpa:latest .
   cd ..
   ```
4. Start the containers with docker-compose (if available in this repo):
   ```bash
   docker-compose up -d
   ```
5. When the logs show the service has started, visit `http://localhost:3333`.
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
