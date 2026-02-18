# Quickstart

This guide shows how to run the Cashu Vault service with Docker Compose or directly from source.

## Prerequisites

- Git
- Docker and Docker Compose
- Java 21
- Maven 3.8+

## Run with Docker Compose

1. Clone the repository:
   ```bash
   git clone https://github.com/398ja/cashu-vault.git
   cd cashu-vault
   ```

2. Create the environment file:
   ```bash
   cp docker.env.example docker.env
   ```
   Edit `docker.env` to set your passwords. The defaults work for local development.

3. Build and start all services:
   ```bash
   docker compose up --build
   ```

4. When the logs show the service has started, the following services are available:

   | Service | URL |
   |---------|-----|
   | Cashu Vault API | http://localhost:3333 |
   | PostgreSQL | localhost:5432 |
   | HashiCorp Vault UI | http://localhost:8200 |

5. Test the service:
   ```bash
   # Create a mint
   curl -X POST http://localhost:3333/vault/mint \
     -H "Content-Type: application/json" \
     -d '{}'

   # List all mints
   curl http://localhost:3333/vault/mint
   ```

Stop the environment with `Ctrl+C` and remove the containers with:
```bash
docker compose down
```

To also remove persistent data volumes:
```bash
docker compose down -v
```

## HashiCorp Vault Backend

By default, private keys are stored in HashiCorp Vault. The `vault-init` container automatically configures the KV v2 secrets engine at `cashu/` and sets up AppRole authentication. When keys are created, their private key material is stored in HashiCorp Vault and the database only retains a `vault_path` reference.

You can inspect the HashiCorp Vault UI at http://localhost:8200 using the token from `VAULT_DEV_ROOT_TOKEN_ID` in your `docker.env`.

To use PostgreSQL instead:

1. Edit `docker.env` and set:
   ```properties
   VAULT_BACKEND=db
   ```

2. Restart the services:
   ```bash
   docker compose up --build
   ```

## Run from Source

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

When running from source, the service uses an embedded H2 database by default. Activate the `prod` profile for PostgreSQL:
```bash
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/cashu_vault \
java -jar cashu-vault-jpa/target/cashu-vault-jpa-*.jar
```

Shut down the service with `Ctrl+C`.
