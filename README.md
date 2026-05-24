# Cashu Vault

Cashu Vault is a Spring Boot service that stores and manages [Cashu](https://cashu.space/) protocol data. It uses PostgreSQL for metadata and proof state, and [HashiCorp Vault](https://www.vaultproject.io/) for secure private key storage.

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

The vault service listens on `http://localhost:3333`, PostgreSQL on `5432`, and HashiCorp Vault on `8200`. Private keys are stored in HashiCorp Vault by default.

## Building

Requires Java 21 and Maven 3.8+.

```bash
mvn package
```

## Running Tests

```bash
# Unit tests
mvn test

# Unit + integration tests (requires Docker)
mvn verify -Pintegration-test
```

Integration tests for the HashiCorp Vault module use [Testcontainers](https://www.testcontainers.org/) and require Docker.

## Docker Images

Docker images for `cashu-vault-jpa` are published to `docker.398ja.xyz/cashu-vault-jpa`, tagged with both the project version and `latest`.

## Security & Network Posture

The vault REST API stores spent-proof evidence and is **not** designed for public exposure (spec 001 / FR-014, Constitution Security Requirements):

- **Service-only API** — deploy on a private subnet behind mTLS (service mesh) or equivalent network gating. Public ingress MUST NOT be permitted. Deployment manifests should enforce this with network policies.
- **Authentication on every write/admin endpoint** — HTTP Basic auth via Spring Security; credentials sourced from environment variables or HashiCorp Vault. **Never commit credentials.** See `cashu.vault.security.users[*]` properties in `cashu-vault-jpa`.
- **Two role tiers**:
  - `ROLE_SERVICE` — service accounts, scoped to a single mint via a `MINT:<uuid>` granted authority. Cleared for inserts, state transitions, and mint-scoped reads.
  - `ROLE_ADMIN` — operator accounts. Required for the tombstone (`POST .../tombstone`) and audit timeline (`GET .../audit`) endpoints; may operate across any mint scope.
- **No physical proof deletion** — `DELETE /vault/proof/{id}` is removed. The only "deletion" path is an admin-only, audited **tombstone** that retains the original `(mint_id, secret, c)` row plus its full Envers history.
- **Mint-scoped lookup** — global secret lookup (`GET /vault/proof/secret/{secret}`) is a 400 stub returning a deprecation envelope in v0.7.x and is removed entirely in v0.8.0. All proof reads require a `mint_id` path variable and cross-check the requesting principal's scope.

For local-dev credentials and an end-to-end walkthrough, see `specs/001-vault-append-only-scoped-lookup/quickstart.md`.

## License

This project is licensed under the [MIT License](LICENSE).
