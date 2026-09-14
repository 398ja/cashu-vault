# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.12.1] - 2026-09-14

### Security

- **CI now scans a resolved SBOM, and runs secret scanning.** Part of closing P8 in the
  2026-09-13 AppSec review: no repository in the estate ran SAST, SCA or secret scanning, and
  scanning the declared tree rather than a resolved one misses everything transitive.

### Fixed

- **The SBOM guard passed when it could not read the component count** — a guard that cannot
  distinguish "zero" from "could not count" reports success for both.
- **A false claim in the gitleaks config.** gitleaks matches the extracted secret, not the
  surrounding line, so a value-based allowlist entry looks like it works and silently does not.

## [0.12.0] - 2026-09-06

Security remediation from the 2026-09-05 audit, plus the defects an adversarial review of that
remediation found. Minor rather than patch: the vault API now requires authentication, an
unrecognised `vault.hashi.auth.method` is refused instead of silently defaulting to token auth,
and the prod Vault listener configuration changed.

### Security

- **The vault API requires a bearer token** (audit C-2). `/vault/**` had no authentication of any
  kind: `GET /vault/proof` returned every stored proof secret to anyone who could reach the port,
  and the mint's keyset private keys were equally exposed. Compared with `MessageDigest.isEqual`,
  no default, and the service refuses to start without one.

- **Proof cryptographic material is kept out of the audit history** (audit H-8). Envers wrote a
  full row copy on every insert, update and delete, including the unblinded signature `C` and the
  NUT-11 witness, and the history row survived deletion of the live row, so deleting a proof did
  not remove its material.

- **The service fails closed when the configured backend is inactive** (audit M-16). This was live
  in production: `docker-compose.prod.yml` set `VAULT_BACKEND=HASHICORP` but not
  `VAULT_HASHI_ENABLED`, so every class in `cashu-vault-hashi` was inactive and keyset private
  keys were stored in Postgres while the configuration said otherwise.

- **Vault paths are validated by ownership, not shape** (audit M-13). The first repair checked that
  `keys/` appeared as a path segment, which `othermount/keys/<other mint>/<other keyset>/1` also
  satisfies, so an authenticated mint could still point a key row at another mint's material and
  read it back. The stored path is now compared against the one path that key is ever written to.
  `HCKeySetVault` had no validation at all and was missed by the first fix.

- **An unrecognised `vault.backend` is refused.** The validator matched only the exact string
  `hashicorp`, so `VAULT_BACKEND=HASHI` reproduced the M-16 incident it existed to prevent.

- **An unrecognised `vault.hashi.auth.method` is refused.** The switch matched the lowercase
  literal and defaulted everything else to token authentication with a null token, so
  `VAULT_HASHI_AUTH_METHOD=APPROLE` silently authenticated the wrong way and made every
  least-privilege policy on the mount decorative.

### Fixed

- **`V9` renumbered to `V1000`.** `V999` is already released with live `ALTER` statements, so every
  database that has booted this service is at schema version 999. With `out-of-order` false and
  `validate-on-migrate` true, a migration numbered 9 fails the migrate and the application does not
  start. A fresh database applies 1..9 then 999 quite happily, which is why CI was green and only
  deployments with real data would have broken. `MigrationVersionOrderingTest` pins the rule.

- **`VaultClient` resolves its API token from the Spring `Environment`**, not only `System.getenv`
  and `System.getProperty`. The vault configures itself with `vault.api.token=${VAULT_API_TOKEN:}`,
  so a deployment following the same convention set a property the client never read and 401ed on
  every call with the token visibly present in configuration. Resolved per request rather than
  captured at construction, since clients are static per entity type.

- **A response-wrapped AppRole secret-id is unwrapped.** The provisioning job wraps the secret-id so
  the credential never lands on disk, and nothing consumed the wrapping token, so the hardened flow
  produced a credential with no supported path into the configuration.

### Changed

- **`cashu-lib` 0.27.0 to 0.30.0** (audit L-36). The vault was three minor versions behind, so none
  of the library's security fixes had reached it.

## [0.11.1] - 2026-08-30

### Fixed

- **`t_keyset.key_set_id` widened from 16 to 66 characters, so a NUT-02 v2 keyset id fits.** A v2 id
  is the version byte `01` followed by a SHA-256 digest in hex, which is 66 characters against the
  16 of a v1 id. The column was sized for v1, so provisioning a mint whose keyset id was derived
  under v2 aborted with `value too long for type character varying(16)` and the provisioning outbox
  retried until it exhausted its attempts, leaving the mint with no keyset at all. `KeySetEntity`
  carried the same 16 in its `@Column(length)` and is widened to match.

## [0.11.0] - 2026-08-29

### Changed

- **BREAKING CHANGE: `t_proof.melt_saga_id` is now `hold_id`, with a new `hold_kind` column.** Two
  flows take an exclusive hold on a proof: the melt saga the column was named for, and the swap hold
  added for cashu-mint#400. Sharing one binding is deliberate, since it is what makes a swap hold
  block a melt on the same proof, but the name said only one of them and the two resolve in
  opposite directions: a stale melt hold is released, while a stale swap hold that reached signing
  must be committed. An operator had to infer which flow produced a row from a `swap-` prefix
  before they could know which action was safe, and the wrong action on a swap hold is a double
  spend. `hold_kind` states it outright.
- The proof vault's REST paths move from `/saga/{id}` to `/hold/{id}`, and the client methods from
  `*ForSaga` to `*ForHold`, for the same reason. Client and server ship together.

## [Unreleased]

## [0.10.1] - 2026-08-28

### Fixed

- **A mint loaded over REST carries its keysets and their keys.**
  `DBMintVault.load` read `MintEntity.getKeySets()` and `DBKeySetVault.load` read
  `KeySetEntity.getKeys()`. Both relations are `@JsonIgnore`, so a client that
  reached those entities over the REST API always saw them empty and built a mint
  advertising no keysets at all. Both now fetch through the endpoints the vault
  serves for exactly this (`/vault/keyset/mint/{mintId}` and
  `/vault/key/keyset/{id}`), and `load(mintId, archive)` honours `archive` so the
  active keyset and the retired ones can be asked for separately.

- **A keyset publishes public keys, not private ones reinterpreted as public.**
  `DBKeySetVault.load` called `PublicKey.fromString(k.getPrivateKey())`, taking a
  private key's bytes as though they were a public key. It now derives the public
  key from the signing key, read through the backend-aware vault: the REST
  representation of a key carries `privateKey: null`, because under the HashiCorp
  backend the row only points at the secret.

- **`VaultClient.retrieveAll()` can deserialise its own entities.** The call
  described its response as `ParameterizedTypeReference<List<T>>`, but `T` is
  erased at that point, so Jackson was handed the abstract `BaseEntity` and
  refused with "no Creators, like default constructor, exist". Every caller
  died on it, including `DBMintVault.load(archive)` — which is every mint
  reading its keysets from the vault, so no mint could be vault-backed at all.
  The response is now requested as an array of the concrete entity type the
  client already holds, which survives erasure.

## [0.10.0] - 2026-08-19

### Fixed

- **A mint can hold more than one keyset per unit, so rotation is possible.**
  `UNIQUE (unit, mint_id)` on `t_keyset` allowed a mint exactly one keyset per
  unit ever, so a replacement keyset could not be inserted while the keyset it
  replaced still existed. Deleting the old one to free the slot is not an
  option: NUT-02 archived keysets must go on verifying and redeeming, so
  removing one strands every token it signed. The constraint is now on active
  keysets only — a mint still has at most one keyset signing per unit, while
  archived keysets accumulate freely. Identity is unchanged;
  `idx_keyset_key_set_mint_unq` still prevents a keyset id being registered
  twice for a mint. (#126)

  Expressed per engine, because H2 has no partial indexes: PostgreSQL uses a
  partial unique index, H2 a generated column that is null while archived.
  Engine-specific migrations live in `db/vendor/{vendor}`, outside the
  recursively scanned `db/migration`.

### Changed

- Updated cashu-lib to 0.21.0 (NUT-11 P2PK secret validation). Validation is fail-closed:
  a malformed P2PK lock is now rejected at parse time rather than accepted and misbehaving later.

## [0.9.1] - 2026-05-24

### Security

- **Mass-assignment guard on `insert-or-claim`.** The insert path now builds
  a fresh server-side `ProofEntity` (new id / version / timestamps) instead
  of persisting the request-body entity, so a caller-supplied `id` can no
  longer turn `save()` into a merge that overwrites an unrelated proof row.

### Fixed

- **Idempotent re-claim for the same saga.** When a proof is already
  `PENDING` and bound to the *requesting* `meltSagaId`, `insertOrClaimForSaga`
  now counts it as bound (client retry after timeout) instead of reporting
  it unclaimable.
- **Narrowed integrity-violation handling.** Only `uk_proof_mint_secret`
  uniqueness violations are treated as the insert race; NOT NULL / FK /
  `(mint_id, c)` violations now propagate instead of being silently counted
  as "not bound".
- **400 on malformed proofs.** Blank `secret` / null `amount` / blank
  `unblindedSignature` are rejected up front (previously a blank secret
  NPE'd into a 500, and missing fields surfaced as a quiet partial-bind).

## [0.9.0] - 2026-05-24

### Added

- **Atomic insert-or-claim for melt-saga proof holds (cashu-mint spec 005)** —
  closes the highest-priority finding in the 2026-05-24 backend token
  integrity review: the melt saga's `PROOFS_HELD` guarantee was not
  actually enforced before external Lightning payment.
  - `ProofRepository.insertOrClaimForSaga(proofs, meltSagaId, mintId)`:
    per proof, claims an existing `UNSPENT` row (via the existing
    `markPending` CAS) or inserts a fresh `PENDING` row already bound
    to the saga, returning the total count durably bound. The existing
    `uk_proof_mint_secret` unique constraint is the race guard; the
    INSERT path retries the claim once on a `(mint_id, secret)`
    conflict so a concurrent inserter never produces a duplicate row.
  - New endpoint `POST /vault/proof/mint/{mintId}/saga/{meltSagaId}/insert-or-claim`
    accepting Y-normalised `ProofEntity` rows; the mint scope is
    resolved server-side so a malformed body cannot bind into a
    different tenant.
  - `ProofClient.insertOrClaimForSaga` + `DBProofVault.insertOrClaimForSaga`
    pass-throughs.
  - 6 integration tests: fresh insert, claim-existing-UNSPENT (no
    duplicate row), partial bind when one proof is held by another
    saga, canonical-identity regression (two calls leave exactly one
    row), empty-body 400, unknown-mint 400.
  - No schema migration — reuses the V4 `melt_saga_id` column and the
    `uk_proof_mint_secret` constraint.

## [0.8.0] - 2026-05-23

### Added

- **Melt-saga binding for cashu-mint spec 002 (T010 + T011)** —
  durable exclusive-hold semantics for proofs participating in a
  NUT-05 melt saga. The FR-006 contract is "a proof PENDING-held
  by a melt saga is held by exactly one saga, ever".
  - V4 Flyway migration: nullable `t_proof.melt_saga_id VARCHAR(64)`
    column + `ix_proof_melt_saga_id` lookup index +
    `t_proof_a.melt_saga_id` audit shadow.
  - `ProofEntity.meltSagaId` field with lifecycle Javadoc.
  - `ProofRepository.markPending(proofIds, meltSagaId, mintId)` —
    application-level CAS gated on
    `state='UNSPENT' AND melt_saga_id IS NULL`.
  - `ProofRepository.commitSpent(meltSagaId)` — atomic
    PENDING → SPENT + clear binding.
  - `ProofRepository.refundToUnspent(meltSagaId)` — atomic
    PENDING → UNSPENT + clear binding.
  - `ProofRepository.clearMeltSaga(meltSagaId)` — best-effort
    cleanup.
  - `ProofRepository.findByMeltSagaId(meltSagaId)` —
    operator-visible enumeration.
  - REST surface on `ProofVaultController`:
    `POST /vault/proof/mint/{mintId}/saga/{meltSagaId}/mark-pending`
    (body `List<String>` of proof secrets),
    `POST /vault/proof/saga/{meltSagaId}/commit-spent`,
    `POST /vault/proof/saga/{meltSagaId}/refund`. All return the
    affected rowcount.
  - `ProofClient` SDK methods `markPending` / `commitSpent` /
    `refund` over the existing `RestTemplate`.
  - `DBProofVault` static pass-through helpers
    `markPendingForSaga` / `commitSpentForSaga` /
    `refundForSaga` for cashu-mint to consume.

### Fixed

- `ProofVaultController.markPending` guards against null / empty
  `proofSecrets` and returns 400 BAD_REQUEST instead of letting
  Hibernate's IN-clause crash with a 500.

## [0.7.0] - 2026-02-18

### Added

- New `cashu-vault-hashi` module for HashiCorp Vault secrets backend using `spring-vault-core` 3.2.0
- `HashiVaultClient` with CAS-aware KV v2 operations (store, get, delete)
- `HCKeyVault` and `HCKeySetVault` implementations that store private keys in HashiCorp Vault
- HashiCorp Vault is the default and only secrets backend for private key material
- `HashiVaultConfig` with support for Token, AppRole and Kubernetes authentication
- `HashiVaultRegistrar` for automatic backend registration on startup
- `VaultClientFactory.Backend` enum with `getVault()` method for backend-agnostic vault access
- `VaultClientFactory.registerHCVault()` for pluggable HashiCorp Vault registration
- `vault_path` column on `KeyEntity` for referencing secrets stored in HashiCorp Vault
- V3 Flyway migration: add `vault_path` column, drop `private_key` column
- HashiCorp Vault service and `vault-init` container in Docker Compose
- `docker.env` / `docker.env.example` for externalized configuration
- Integration tests for `HashiVaultClient` using Testcontainers VaultContainer
- `application-hashicorp.properties` default profile for HashiCorp Vault backend

### Changed

- `VaultClientFactory` defaults to HashiCorp Vault backend
- `KeyEntity.privateKey` is now `@Transient` (not persisted to database)
- Docker Compose refactored to use `env_file` instead of inline environment variables
- Docker Compose now includes persistent volumes for PostgreSQL and Vault data

### Security

- Private keys can now be stored in HashiCorp Vault with KV v2 versioning and audit logging
- AppRole least-privilege policy restricts access to `cashu/` mount paths only
- `docker.env` is gitignored to prevent credential leakage

## [0.6.0] - 2026-02-03

### Added

- Production Spring profile (`application-prod.properties`) with PostgreSQL and HikariCP configuration
- G1GC garbage collector configuration in Dockerfile with heap sizing and GC logging
- Bean Validation annotations (`@NotBlank`, `@Pattern`, `@Size`, `@Positive`) on all controller path parameters
- `GlobalExceptionHandler` for centralized exception handling with sanitized error messages

### Changed

- Updated cashu-lib dependency to 0.16.0
- Use `TreeMap` instead of `HashMap` in `DBMintVault.getKeys()` to prevent hash collision DoS
- Return defensive copies (unmodifiable collections) from `MintEntity` and `KeySetEntity` getters
- Initialize collections with known sizes for better performance
- HikariCP connection pool tuning (pool size, timeouts, prepared statement caching)

### Security

- Implement Oracle Secure Coding Guidelines for Java SE (83% compliance)
- Exception message sanitization to prevent information disclosure
- Input validation on all REST endpoints to prevent injection attacks

### Performance

- Implement Java Performance Tuning recommendations (80% compliance)
- Configure G1GC with 200ms pause time target
- Add heap dump on OOM for production diagnostics

## [0.5.0] - 2026-01-26

### Added

- Database unique constraints on `(mint_id, secret)` and `(mint_id, C)` to prevent duplicate proof storage
- SHA-256 fingerprint column for token-level duplicate detection with automatic computation via `@PrePersist`
- `insertIfNotExists()` method in ProofRepository with `InsertResult` record for duplicate handling
- `existsByMint_IdAndSecret()`, `existsByMint_IdAndUnblindedSignature()`, and `findByFingerprint()` repository methods
- Comprehensive integration tests for duplicate detection scenarios

### Changed

- ProofVaultController now returns HTTP 409 Conflict when storing duplicate proofs
- Updated cashu-lib dependency to 0.13.0
- Configured maven-surefire-plugin 3.5.3 for JUnit 5 support

### Security

- Implemented proof uniqueness constraints as part of security hardening (P0-VAULT-001 through P0-VAULT-004)

## [0.4.6] - 2026-01-10

### Changed

- Updated cashu-lib dependency to 0.11.1
- Added comprehensive coding guidelines to AGENTS.md (Clean Code, SOLID principles, Design Patterns)

## [0.4.5] - 2025-12-XX

### Changed

- Version bump release

## [0.4.4] - 2025-12-XX

### Changed

- Version bump release

## [0.4.3] - 2025-12-XX

### Changed

- Updated cashu-lib to 0.7.2

## [0.4.1] - 2025-12-XX

### Changed

- Updated cashu-lib to 0.7.0

## [0.4.0] - 2025-12-XX

### Changed

- Updated cashu-lib to 0.6.2

### Fixed

- Aligned hibernate-envers version with hibernate-core

[Unreleased]: https://github.com/398ja/cashu-vault/compare/v0.10.0...HEAD
[0.10.0]: https://github.com/398ja/cashu-vault/compare/v0.9.1...v0.10.0
[0.8.0]: https://github.com/398ja/cashu-vault/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/398ja/cashu-vault/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/398ja/cashu-vault/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/398ja/cashu-vault/compare/v0.4.6...v0.5.0
[0.4.6]: https://github.com/398ja/cashu-vault/compare/v0.4.5...v0.4.6
[0.4.5]: https://github.com/398ja/cashu-vault/compare/v0.4.4...v0.4.5
[0.4.4]: https://github.com/398ja/cashu-vault/compare/v0.4.3...v0.4.4
[0.4.3]: https://github.com/398ja/cashu-vault/compare/v0.4.1...v0.4.3
[0.4.1]: https://github.com/398ja/cashu-vault/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/398ja/cashu-vault/releases/tag/v0.4.0
