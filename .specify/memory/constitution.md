<!--
  Sync Impact Report
  ==================
  Version change: 0.0.0 (template) -> 1.0.0
  Modified principles: N/A (initial population)
  Added sections:
    - 6 Core Principles (Proof State Integrity, Audit Trail,
      Clean Architecture, Testing Discipline, Virtual Threads,
      Secure Coding & Code Quality)
    - Security Requirements
    - Development Workflow
    - Governance
  Removed sections: None (template placeholders replaced)
  Adapted from cashu-mint constitution (1.1.0):
    - Renamed Principle I "Token Integrity" -> "Proof State
      Integrity" (vault is the storage authority, not the issuance
      authority)
    - Renamed Principle II "Protocol Compliance (Cashu NUTs)" ->
      "Audit Trail" (vault does not implement NUTs directly; the
      mint does. The vault's existential concern is its history)
    - Kept Principles III–VI structurally identical
  Templates requiring updates:
    - .specify/templates/plan-template.md — ✅ compatible
    - .specify/templates/spec-template.md — ✅ compatible
    - .specify/templates/tasks-template.md — ✅ compatible
  Follow-up TODOs: None
-->

# cashu-vault Constitution

## Core Principles

### I. Proof State Integrity (NON-NEGOTIABLE)

The vault is the durable store of Cashu proof state for one or
more mints. Every code path that creates, mutates, or queries
proof state MUST preserve the following invariants:

- **Append-only history**: proofs MUST NOT be physically deleted
  once a state transition has been recorded against them.
  Spent and archived proofs are retained as evidence; "deletion"
  is expressed as a privileged, audited tombstone, never a
  destructive SQL `DELETE`
- **Mint-scoped uniqueness**: the `(mint_id, secret)` and
  `(mint_id, c)` unique constraints on `ProofEntity` are
  load-bearing and MUST be preserved. Cross-mint duplicate
  secrets are valid and MUST be resolved separately
- **Mint-scoped lookup**: every proof retrieval, state-transition,
  and deletion API MUST require `mint_id` (or its equivalent
  scoping token) and MUST resolve against
  `(mint_id, secret)` / `(mint_id, c)`. Global secret lookup
  is forbidden — it can return the wrong proof when two mints
  use the same secret
- **Atomic state transitions**: proof state changes
  (`UNSPENT → PENDING → SPENT`, etc.) MUST be expressed as
  durable database transactions with optimistic or pessimistic
  locking. In-memory caches MUST NOT be the source of truth for
  write decisions
- **`insertIfNotExists` is the only safe insertion path**: every
  proof creation MUST catch the unique-constraint violation and
  treat it as idempotent success when the existing row matches,
  or as a hard rejection when it does not. Naked inserts that
  bypass the constraint are forbidden
- **No silent overwrite**: an update that would change a proof's
  `secret`, `c`, or `mint_id` MUST be rejected. Only state-machine
  columns (e.g. `state`, `spent_at`, `pending_owner`) are mutable

Proof-state-integrity violations are blocking defects.
Performance, ergonomics, and refactor cleanliness MUST yield to
integrity.

### II. Audit Trail

The vault is the system of record for proof-state history.
Auditability is treated as a first-class concern, not a logging
afterthought:

- **Hibernate Envers** is the canonical mechanism for capturing
  state-transition history on proof entities. Schema changes
  that affect audited fields MUST update the corresponding
  `_AUD` table migration in lock-step
- **Append-only audit rows**: audit rows MUST never be
  edited or deleted from application code; retention/archival
  is governed by an explicit operator policy
- **Operator-visible alerts**: every rejected insert (constraint
  violation), every rejected delete (admin-only path), and every
  mint-id scope violation MUST be logged with structured fields
  suitable for SIEM ingestion
- **Audit clock**: state-transition timestamps MUST come from the
  database (`now()`), not the application JVM, to avoid clock
  skew between vault replicas

Cashu protocol shapes consumed by the vault (proof structure,
secret format, signature format) are defined by the upstream
[Cashu NUT specifications](https://github.com/cashubtc/nuts) —
in particular [NUT-00](https://github.com/cashubtc/nuts/blob/main/00.md)
(cryptography and models) and
[NUT-07](https://github.com/cashubtc/nuts/blob/main/07.md)
(token state check). The vault does not implement NUT endpoints
directly — the mint does — but field semantics MUST stay aligned
with the linked spec revisions, and any breaking change to
`secret` or `c` representation MUST be reviewed against those
specs before the schema migration ships.

### III. Clean Architecture

Every module MUST follow Clean Architecture with inward-pointing
dependencies:

- **domain**: Pure entities (proof, keyset) with zero external
  dependencies
- **application** / **service**: Use cases depending only on
  domain abstractions and persistence ports
- **api**: REST controllers and DTOs; controllers MUST delegate
  to services and MUST NOT contain persistence logic, query
  building, or transaction management
- **infrastructure**: JPA repositories, Hashi (HashiCorp Vault)
  client, Flyway migrations

The Hexagonal (Ports & Adapters) and Repository patterns are
mandatory. JPA / Hibernate / SQL MUST NOT leak into the
controller or service layer. Module split mirrors the codebase:

- `cashu-vault-jpa` — JPA entities, repositories, Flyway
  migrations, Envers configuration
- `cashu-vault-api` — REST controllers, DTOs, service
  orchestration
- `cashu-vault-hashi` — HashiCorp Vault integration for
  sensitive keyset material

### IV. Testing Discipline

All code MUST meet the following testing standards:

- **Unit tests** (`*Test.java`): Run via `mvn -q test`; every
  test method MUST have a plain-English comment describing the
  scenario
- **Integration tests** (`*IT.java`): Use Testcontainers with
  PostgreSQL — never H2 for write-path tests, because H2 does
  not faithfully reproduce PostgreSQL's unique-constraint
  semantics or row locking
- Tests MUST exercise realistic scenarios: cross-mint duplicate
  secrets, repeated `insertIfNotExists` under concurrent load,
  state-transition races, unauthorised deletion attempts,
  global-secret-lookup attempts that must be rejected
- Coverage gate enforced by `mvn verify`; the proof state
  machine MUST be covered by both unit and integration tests
- Hibernate Envers audit rows MUST be asserted on in
  state-transition tests; missing audit rows are a regression

### V. Virtual Threads for Concurrency

Java 21 Virtual Threads (Project Loom) are the standard
concurrency model for I/O-bound work in the vault:

- Use `Executors.newVirtualThreadPerTaskExecutor()` with
  `CompletableFuture` for parallel I/O when the same caller
  inspects many proofs in one logical request
- Use `ReentrantLock` instead of `synchronized` to avoid VT
  pinning
- Spring Boot VT support is enabled via
  `spring.threads.virtual.enabled=true`
- HikariCP connection pool sizing MUST be tuned for VT scale
  (the default of `maxPoolSize = 10` is too small if every
  request fans out across virtual threads)
- Database transactions MUST be short-lived; VTs MUST NOT hold
  a connection across an external HTTP call

### VI. Secure Coding & Code Quality

- Input validation at system boundaries — REST controllers MUST
  validate `mint_id`, `secret`, and `c` shape before the service
  layer
- Output encoding to prevent injection in any human-facing
  surface (admin endpoints, logs)
- Safe cryptography via the existing libraries; no hand-rolled
  hashing or signature checks
- Secrets MUST live in environment variables, HashiCorp Vault,
  or a secret manager — never in code, configs, or commits
- OWASP Top 10 vulnerabilities are blocking defects
- Follow SOLID principles; use Java records or Lombok to reduce
  boilerplate
- Prefer unchecked exceptions with context (operation +
  failure type + structured fields)
- YAGNI: no speculative abstractions; three similar lines are
  better than a premature helper

## Security Requirements

- **Service-only API**: the vault REST API MUST NOT be exposed
  on a public network. Network controls (private subnet,
  service-mesh mTLS, or equivalent) MUST gate every endpoint
- **Authentication on every write/admin endpoint**: insert,
  state-transition, delete, and archive endpoints MUST require
  service-account authentication. Unauthenticated calls MUST
  return 401 / 403 before any database access
- **Authentication on every read endpoint that exposes secrets**:
  any endpoint returning `secret` or `c` MUST require an
  authenticated principal scoped to the requesting `mint_id`
- **No public deletion**: physical proof deletion MUST be
  removed from public surfaces. Where deletion is legally
  required (GDPR, etc.), it MUST be expressed as a privileged
  tombstone written by an audited admin path
- **No secrets in commits**: `.env`, credentials, HashiCorp
  tokens, and database passwords MUST be excluded by
  `.gitignore` and verified by pre-commit hooks
- **Security event logging**: every rejected request, every
  constraint violation, every scope-mismatch, and every admin
  action MUST be logged with structured fields
- **Dependency vulnerabilities**: critical CVEs in transitive
  deps MUST be addressed within one release cycle
- **Audit retention**: Envers `_AUD` tables MUST NOT be purged
  by application code; retention/archival is governed by an
  explicit operator policy

## Development Workflow

- **Commits**: Conventional Commits format:
  `feat(scope):`, `fix(scope):`, `docs(scope):`, etc. The
  `scope` SHOULD identify the affected module (`jpa`, `api`,
  `hashi`, `migrations`, etc.)
- **Builds**: `mvn -q verify` MUST pass before committing
- **Migrations**: every schema change MUST ship as a Flyway
  migration in `cashu-vault-jpa/src/main/resources/db/migration`
  with the matching Envers migration; out-of-order migrations
  are forbidden. Validate against the existing baseline before
  merge
- **Versions**: Managed in the parent `pom.xml` properties
  section; use `/bumpup` for coordinated bumps across
  producer/consumer pin chains. Consumer (cashu-mint) version
  pin MUST be updated in the same release train when the vault
  contract changes
- **Branching**: Feature branches off `develop`; PRs target
  `develop`; `master` tracks released versions
- **Code review**: All PRs require review; financial / proof-
  state changes MUST be reviewed by a second maintainer with
  explicit attention to Principles I and II

## Governance

This constitution is the authoritative source of project
standards for cashu-vault. It supersedes ad-hoc practices and
informal conventions.

- **Amendments**: Any change to this constitution MUST be
  documented with rationale, reviewed by a maintainer, and
  reflected in the version below. A Sync Impact Report at the
  top of the file MUST summarise the change.
- **Versioning**: MAJOR for principle removals/redefinitions,
  MINOR for new principles or material expansions, PATCH for
  clarifications and wording fixes.
- **Compliance**: All PRs and code reviews MUST verify
  adherence to these principles. Violations of Principle I
  (Proof State Integrity) or Principle II (Audit Trail) are
  blocking and require maintainer sign-off to merge under any
  exception clause.
- **Runtime guidance**: See repository `README.md`, the
  consumer-side `CLAUDE.md` in cashu-mint, and module-level
  documentation under `cashu-vault-*/` for build commands,
  schema layout, and operational patterns.

**Version**: 1.0.0 | **Ratified**: 2026-05-22 | **Last Amended**: 2026-05-22
