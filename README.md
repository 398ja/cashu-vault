# Cashu Vault

Cashu Vault is a Spring Boot service that stores and manages Cashu data using a PostgreSQL database.

Full documentation, including tutorials, how-to guides, reference material, and explanations, is available in the [docs](docs/) directory.

## Docker deployment

Run `./mvnw deploy` to build and push the service's Docker image to `docker.398ja.xyz/cashu-vault-jpa`.

Each build is published with two tags:

- The project's version (for example, `docker.398ja.xyz/cashu-vault-jpa:0.1.0`).
- `latest`, allowing consumers to pull the most recent image without specifying a version.
