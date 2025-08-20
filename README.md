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

If Maven is not installed globally, use the provided wrapper in
`cashu-vault-jpa/`:

```bash
./cashu-vault-jpa/mvnw package
```

This creates the runnable JAR `cashu-vault-jpa/target/cashu-vault-jpa-*.jar` used in the Docker image.

## Running locally

The application can be started directly on your machine once it has been
built. Execute the jar produced by the build or run it from source:

```bash
java -jar cashu-vault-jpa/target/cashu-vault-jpa-*.jar
# or
./cashu-vault-jpa/mvnw spring-boot:run
```

The service will then be available at `http://localhost:3333`.

## Running with Docker Compose

The repository includes a `docker-compose.yml` which starts the vault service together with a PostgreSQL container:

```bash
docker-compose up --build
```

The application listens on port `3333` by default and the database on `5432`.
Set the `cashu_vault_port` environment variable to run the service on a different port.
Datasource settings can be customised with the environment variables defined in
`docker-compose.yml`.

## Running Tests

Unit tests run against an in-memory H2 database and can be executed with:

```bash
mvn test
```

## Configuration

`VaultClient` reads the base URL of the service from the `VAULT_BASE_URL`
environment variable or the `vault.base.url` system property. If neither is
set, it falls back to `http://localhost:${cashu_vault_port}` with a default port of `3333`.
The provided Docker image defines `cashu_vault_port` so the service defaults to
`http://localhost:3333` when started with Docker Compose.

## License

This project is licensed under the [MIT License](LICENSE).
