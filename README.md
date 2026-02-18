# Cashu Vault

Cashu Vault is a Spring Boot service that stores and manages [Cashu](https://cashu.space/) protocol data. It uses PostgreSQL for metadata and proof state, with [HashiCorp Vault](https://www.vaultproject.io/) as the default backend for secure private key storage.

Full documentation is available in the [docs](docs/) directory.

## Modules

| Module | Description |
|--------|-------------|
| **cashu-vault-jpa** | Spring Boot application with REST controllers, JPA entities and Flyway migrations |
| **cashu-vault-api** | Client library with `Vault<T>` interface, DB implementations and `VaultClientFactory` |
| **cashu-vault-hashi** | HashiCorp Vault backend using `spring-vault-core` for secrets management |

## Quick Start

```bash
# 1. Copy and configure the environment file
cp docker.env.example docker.env

# 2. Start all services
docker compose up --build
```

The vault service listens on `http://localhost:3333`, PostgreSQL on `5432`, and HashiCorp Vault on `8200`.

HashiCorp Vault is the default secrets backend. To use PostgreSQL instead, set `VAULT_BACKEND=db` in `docker.env`.

## Building

Requires Java 21 and Maven 3.8+.

```bash
mvn package
```

## Running Tests

```bash
mvn test
```

Integration tests for the HashiCorp Vault module use [Testcontainers](https://www.testcontainers.org/) and require Docker.

## Docker Images

Docker images for `cashu-vault-jpa` are published to `docker.398ja.xyz/cashu-vault-jpa`, tagged with both the project version and `latest`.

## License

This project is licensed under the [MIT License](LICENSE).
