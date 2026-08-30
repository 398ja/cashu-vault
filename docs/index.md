# Cashu Vault

Cashu Vault is a Spring Boot service that stores and manages [Cashu](https://cashu.space/) protocol data.
It provides REST endpoints for persisting **mints**, **key sets**, **keys**, and **proofs**. Private keys are stored in **HashiCorp Vault** (KV v2) by default, while metadata and proof state remain in **PostgreSQL**.

## Modules

| Module | Description |
|--------|-------------|
| **cashu-vault-jpa** | Spring Boot application with REST controllers, JPA entities and Flyway migrations |
| **cashu-vault-api** | Client library with `Vault<T>` interface, DB implementations and `VaultClientFactory` |
| **cashu-vault-hashi** | HashiCorp Vault backend using `spring-vault-core` (KV v2 secrets engine) |

## Documentation

- [Tutorials](tutorials/) - Step-by-step guides to get started
- [How-To Guides](how-to/) - Solutions for common tasks, including [proof holds](how-to/work-with-proof-holds.md)
- [Reference](reference/) - API and configuration details
- [Explanation](explanation/) - Architecture and design decisions

## Building

Java 21 and Maven 3.8+ are required. Compile all modules with:

```bash
mvn package
```

If Maven is not installed globally, use the provided wrapper in `cashu-vault-jpa/`:

```bash
./cashu-vault-jpa/mvnw package
```

This creates the runnable JAR `cashu-vault-jpa/target/cashu-vault-jpa-*.jar` used in the Docker image.

## Running with Docker Compose

The repository includes a `docker-compose.yml` which starts the vault service together with PostgreSQL and HashiCorp Vault.

```bash
# 1. Create and configure the environment file
cp docker.env.example docker.env
# Edit docker.env to set passwords and preferences

# 2. Start all services
docker compose up --build
```

### Services

| Service | Port | Description |
|---------|------|-------------|
| `cashu-vault-jpa` | 3333 | The main vault REST API |
| `cashu-vault-db` | 5432 | PostgreSQL database |
| `hashicorp-vault` | 8200 | HashiCorp Vault (dev mode) |
| `vault-init` | - | One-shot container that configures the KV v2 engine and AppRole auth |

### Secrets Backend

Private keys are stored in HashiCorp Vault by default. The `vault-init` container automatically configures the KV v2 engine and AppRole auth. See [Configuration Reference](reference/configuration.md) for all options.

## Running Locally

```bash
java -jar cashu-vault-jpa/target/cashu-vault-jpa-*.jar
# or
./cashu-vault-jpa/mvnw spring-boot:run
```

The service will be available at `http://localhost:3333`.

## Running Tests

Unit tests run against an in-memory H2 database. Integration tests for the HashiCorp Vault module use Testcontainers and require Docker.

```bash
# Unit tests
mvn test

# Unit + integration tests (requires Docker)
mvn verify -Pintegration-test
```

## REST API Overview

All endpoints are relative to `/vault`. See the full [API Reference](reference/api.md) for details.

### Mint
- `POST   /mint` - store a mint
- `GET    /mint/{id}` - retrieve by ID
- `POST   /mint/archive/{id}` - mark archived
- `DELETE /mint/{id}` - remove

### Key Set
- `POST   /keyset` - store a key set
- `GET    /keyset/{id}` - retrieve by ID
- `GET    /keyset/id/{id}` - retrieve by key set ID
- `GET    /keyset/unit/{unit}` - all key sets for a unit
- `GET    /keyset/mint/{mintId}` - key sets for a mint
- `POST   /keyset/archive/{id}` - mark archived
- `DELETE /keyset/{id}` - remove

### Key
- `POST   /key` - store a key
- `GET    /key/{id}` - retrieve by ID
- `GET    /key/unit/{unit}` - keys by unit
- `GET    /key/keyset/{id}` - keys by key set ID
- `POST   /key/archive/{id}` - mark archived
- `DELETE /key/{id}` - remove

### Proof
- `POST   /proof` - store a proof (returns 409 on duplicate)
- `GET    /proof/{id}` - retrieve by ID
- `GET    /proof/mint/{mintId}` - proofs by mint ID
- `POST   /proof/archive/{id}` - mark archived
- `DELETE /proof/{id}` - remove

## License

This project is licensed under the [MIT License](../LICENSE).
