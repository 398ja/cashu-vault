# Cashu Vault

Cashu Vault is a Spring Boot service that stores and manages Cashu data using a PostgreSQL database.

Full documentation, including tutorials, how-to guides, reference material, and explanations, is available in the [docs](docs/) directory.

## Features

- Implements NUT-09 restore support by persisting blinded messages and blind signatures. The new
  `/vault/blindsignature` REST endpoints allow storing and retrieving previously issued blind signatures.

## Docker images

Docker images for `cashu-vault-jpa` are published to `docker.398ja.xyz/cashu-vault-jpa`.
Each image is tagged with both the project version and `latest`, allowing consumers to pull the most recent build without specifying a version.
