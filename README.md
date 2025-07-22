# Cashu Vault

Cashu Vault is a Spring Boot service that stores and manages Cashu data using a PostgreSQL database. 
It provides REST endpoints for persisting **mints**, **key sets**, **proofs**, and **keys**. The project is composed of two Maven modules:

- **cashu-vault-jpa** – Spring Boot application containing the REST controllers and JPA entities.
- **cashu-vault-api** – API layer with configuration objects and helper classes for interacting with the vault service.

## Building

Java 21 and Maven 3.8+ are required. Compile all modules with:

```bash
mvn package
```

This creates the runnable JAR `cashu-vault-jpa/target/cashu-vault-jpa-*.jar` used in the Docker image.

## Running with Docker Compose

The repository includes a `docker-compose.yml` which starts the vault service together with a PostgreSQL container:

```bash
docker-compose up --build
```

The application listens on port `3333` and the database on `5432`.  Datasource
settings can be customised with the environment variables defined in
`docker-compose.yml`.

## Running Tests

Unit tests are executed with:

```bash
mvn test
```

## REST API Overview

The service exposes the following endpoints (all relative to `/vault`):

### Mint
- `POST   /mint` – store a mint
- `GET    /mint/{id}` – retrieve by ID
- `POST   /mint/archive/{id}` – mark archived
- `DELETE /mint/{id}` – remove

### Key set
- `POST   /keyset` – store a key set
- `GET    /keyset/{id}` – retrieve by ID
- `GET    /keyset/id/{id}` – retrieve by key set ID
- `GET    /keyset/unit/{unit}` – all key sets for a unit
- `GET    /keyset/mint/{mintId}/unit/{unit}/keyset/{keySetId}` – key set for a mint, unit and ID
- `GET    /keyset/mint/{mintId}` – key sets for a mint
- `POST   /keyset/archive/{id}` – mark archived
- `DELETE /keyset/{id}` – remove

### Key
- `POST   /key` – store a key
- `GET    /key/{id}` – retrieve by ID
- `GET    /key/unit/{unit}` – keys by unit
- `GET    /key/privatekey/{privateKey}` – retrieve by private key
- `GET    /key/keyset/{id}` – keys by key set ID
- `POST   /key/archive/{id}` – mark archived
- `DELETE /key/{id}` – remove

### Proof
- `POST   /proof` – store a proof
- `GET    /proof/{id}` – retrieve by ID
- `GET    /proof/mint/{mintId}` – proofs by mint ID
- `POST   /proof/archive/{id}` – mark archived
- `DELETE /proof/{id}` – remove

## Configuration

`VaultClient` reads the base URL of the service from the `VAULT_BASE_URL`
environment variable or the `vault.base.url` system property. The provided
Docker image defines `VAULT_BASE_URL` so the service defaults to
`http://localhost:3333` when started with Docker Compose.

## License

This project is provided without any specific license information.
