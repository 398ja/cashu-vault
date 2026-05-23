# Architecture Overview

Cashu Vault is organised as a multi-module Maven project that separates the running service, client utilities, and secrets backend into distinct modules. This document describes the role of each module, the flow of data through the system and important design decisions.

## Module Roles

- **cashu-vault-jpa** - Spring Boot application containing the REST controllers, JPA entities, repositories and Flyway migrations. This is the deployed service.
- **cashu-vault-api** - Client library providing the `Vault<T>` interface, `DBVault` base class and `VaultClientFactory`. External consumers depend on this module to interact with the vault service.
- **cashu-vault-hashi** - HashiCorp Vault backend that implements the `Vault<T>` interface for key entities, storing private keys in a KV v2 secrets engine. This is the default and only secrets backend for key material.

## System Architecture

```
                                HTTP/REST
  Consumers ─────────────────────────────────────> cashu-vault-jpa
  (cashu-vault-api)                                (Spring Boot)
                                                     │
          ┌──────────────────────────────────────────┤
          │                                          │
          │  HTTPS (mTLS)                   Spring Data JPA
          │                                          │
  ┌───────▼────────────┐                 ┌───────────▼───────────┐
  │  HashiCorp Vault   │                 │     PostgreSQL        │
  │                    │                 │                       │
  │  KV v2 Engine:     │                 │  t_keyset (metadata)  │
  │   /cashu/keys/*    │                 │  t_mint   (metadata)  │
  │   /cashu/seeds/*   │                 │  t_proof  (state)     │
  │                    │                 │  t_key    (metadata + │
  │  AppRole Auth      │                 │    vault_path ref)    │
  └────────────────────┘                 └───────────────────────┘
```

## Secrets Backend

Private keys are stored exclusively in HashiCorp Vault's KV v2 engine. The database retains metadata and a `vault_path` reference to the secret. On retrieval, the key is enriched from HashiCorp Vault transparently.

At startup, the `HashiVaultRegistrar` registers `HCKeyVault` and `HCKeySetVault` implementations with the `VaultClientFactory`. Entity types without a HashiCorp implementation (e.g., `ProofEntity`) use the database backend automatically.

## Data Flow

1. External callers use the API module which creates a `VaultClient` for a specific entity type. The client resolves the service base URL from environment variables and performs HTTP requests against the `/vault` endpoints.
2. The service module maps these requests to controllers such as `MintVaultController`. Controllers delegate to Spring Data repositories.
3. Repositories read and write JPA entities which Hibernate translates into SQL for PostgreSQL.
4. For key entities, `HCKeyVault` writes the private key to HashiCorp Vault at `cashu/keys/{mint_id}/{keyset_id}/{amount}`, then persists the entity to PostgreSQL with `vault_path` pointing to the Vault secret. Private keys are never stored in the database.
5. On retrieval, the key entity is loaded from PostgreSQL and the private key is fetched from HashiCorp Vault and populated on the entity before returning. This is transparent to callers of the `Vault<T>` interface.

## HashiCorp Vault Topology

```
cashu/                          # KV v2 secrets engine mount
├── keys/
│   └── {mint_id}/
│       └── {keyset_id}/
│           └── {amount}        # { "private_key": "...", "created_at": "..." }
├── seeds/
│   └── {mint_id}               # { "mnemonic": "...", "seed_hex": "..." }
└── metadata/
    └── {mint_id}               # { "owner": "...", "rotation_policy": "..." }
```

## Database Schema

All entities inherit from `BaseEntity` which provides:

- `id` (UUID) - pre-assigned random identifier
- `archived` (boolean) - soft-delete flag
- `created_at` / `updated_at` - audit timestamps
- `version` (integer) - optimistic locking

Key tables:

| Table | Purpose |
|-------|---------|
| `t_mint` | Mint identity |
| `t_keyset` | Key set metadata (unit, key set ID, mint reference) |
| `t_key` | Key metadata and vault path reference (private keys stored in HashiCorp Vault) |
| `t_proof` | Proof state (secret, unblinded signature, state machine) |
| `*_a` tables | Hibernate Envers audit history |

Flyway manages schema migrations:

- **V1** - Initial schema
- **V2** - Proof unique constraints
- **V3** - Add `vault_path` to `t_key`, drop `private_key` column

## Design Choices

- **Separation of concerns** - Secrets in HashiCorp Vault, metadata in PostgreSQL. The database never needs to store private keys when the HashiCorp backend is active.
- **Reference-based linking** - `KeyEntity.vaultPath` acts as a foreign key to HashiCorp Vault, linking the database record to its secret.
- **Transparent abstraction** - The `Vault<T>` interface is unchanged. Consumers are unaware of the underlying storage mechanism.
- **Pluggable registration** - `VaultClientFactory.registerHCVault()` allows the hashi module to register itself without the API module depending on it directly.
- **Generic REST client** - `VaultClient<T>` and `DBVault<T>` abstract CRUD operations for all entity types, reducing boilerplate.
- **Auditing and soft deletion** - Hibernate Envers tracks every write. The `archived` flag supports soft deletes.
- **Environment-driven configuration** - All settings are externalised via `docker.env` and Spring properties.
