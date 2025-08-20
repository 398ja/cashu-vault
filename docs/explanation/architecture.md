# Architecture Overview

Cashu Vault is organised as a multi-module Maven project that separates the running service from the client utilities. This document describes the role of each module, the flow of data through the system and important design decisions.

## Module roles

* **cashu-vault-jpa** – Spring Boot application containing the REST controllers, JPA entities and repositories that expose the vault service over HTTP.
* **cashu-vault-api** – Client library providing `DBVault` wrappers and a factory for creating REST clients that interact with the service.

## Data flow

1. External callers use the API module which creates a `VaultClient` for a specific entity type. The client resolves the service base URL from environment variables or system properties and performs HTTP requests against the `/vault` endpoints.
2. The service module maps these requests to controllers such as `MintVaultController`. Controllers delegate to Spring Data repositories which handle persistence logic.
3. Repositories read and write JPA entities which Hibernate translates into SQL for the configured database.

## Database usage

The application targets PostgreSQL by default, with connection settings and dialect specified in `application.properties`. Entities inherit from a shared `BaseEntity` that supplies a UUID identifier, archival flag and auditing timestamps, enabling soft deletes and change tracking.

## Design choices

* **Separation of concerns** – splitting the service and client logic into separate modules keeps the running application lean while offering a reusable client library.
* **Generic REST client** – `VaultClient` and `DBVault` abstract CRUD operations for different entity types, reducing boilerplate across controllers and client code.
* **Auditing and soft deletion** – the shared base entity provides audit fields and an `archived` flag so data can be marked inactive without removal.
* **Environment-driven configuration** – the client resolves the service URL from `VAULT_BASE_URL` or a provided port, easing deployment in varied environments.

