# Feature Specification: Append-Only Proof Storage and Mint-Scoped Lookup

**Feature Branch**: `001-vault-append-only-scoped-lookup`
**Created**: 2026-05-22
**Status**: Draft
**Input**: Backend Token Integrity Review (2026-05-22) — finding "Medium: Vault has strong uniqueness constraints, but administrative deletion and unscoped proof lookup are risky".
**Source Repository**: `cashu-vault`
**Code Touch Points** (paths corrected 2026-05-24 per `/speckit.analyze` finding I2):
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/model/ProofEntity.java:38`
  — existing `(mint_id, secret)` and `(mint_id, c)` unique
  constraints (load-bearing, retained)
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/repos/ProofRepository.java:122`
  — proof `insertIfNotExists` (the catch-block targeted by FR-009)
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/repos/ProofRepository.java:24`
  — `findBySecret(String secret)` global-lookup overload to be
  removed (FR-005)
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/controller/ProofVaultController.java:51`
  — proof insert endpoint (`POST /vault/proof`)
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/controller/ProofVaultController.java:131`
  — `GET /vault/proof/secret/{secret}` global-secret-keyed read
  path (FR-005, to become a 400 stub)
- `cashu-vault-jpa/src/main/java/xyz/tcheeric/cashu/vault/db/controller/ProofVaultController.java:237`
  — `DELETE /vault/proof/{id}` physical deletion endpoint (FR-001,
  to be removed and replaced with admin tombstone)

> **Note:** the controllers currently live in `cashu-vault-jpa`
> rather than `cashu-vault-api`. This is acknowledged by the
> Constitution v1.0.0 governance as a pre-existing structural
> debt; the relocation is out of scope for this spec and tracked
> for a follow-up consolidation spec.

## Constitution Alignment

This feature is governed by the cashu-vault Constitution v1.0.0
(`.specify/memory/constitution.md`). Every requirement below
traces to one or more principles.

- **Principle I — Proof State Integrity (NON-NEGOTIABLE)**:
  "append-only history" (FR-001, FR-002, FR-003),
  "mint-scoped uniqueness" (FR-004 — preserve the existing
  constraints), "mint-scoped lookup" (FR-005, FR-006, FR-007),
  "no silent overwrite" (FR-008), `insertIfNotExists` as the
  only safe insertion path (FR-009).
- **Principle II — Audit Trail**: every state transition,
  every rejected mutation, and every admin tombstone MUST be
  captured via Hibernate Envers and structured logs
  (FR-010, FR-011, SC-004). Audit clock comes from the
  database `now()`, not the JVM (FR-012).
- **Principle III — Clean Architecture**: deletion / tombstone
  logic lives in the service layer; controllers MUST NOT
  invoke repository `deleteBy*` directly.
- **Principle IV — Testing Discipline**: Testcontainers
  PostgreSQL is mandatory for the write-path tests; H2 is
  rejected because it does not faithfully reproduce
  PostgreSQL constraint semantics under concurrent inserts.
- **Security Requirements**: physical proof deletion is
  removed from public surfaces; authentication is required
  on every write / admin endpoint and on every secret-
  returning read endpoint; the API MUST NOT be exposed on a
  public network.

Cashu protocol shape references:
[NUT-00 — Cryptography and Models](https://github.com/cashubtc/nuts/blob/main/00.md)
defines the `secret` and `C` fields the vault stores;
[NUT-07 — Token state check](https://github.com/cashubtc/nuts/blob/main/07.md)
defines the state-transition semantics consumed via the
vault.

## Background and Problem Statement

The vault has two strong points:

- `ProofEntity` declares unique constraints on
  `(mint_id, secret)` and `(mint_id, c)`. These are correct
  and load-bearing.
- The service layer catches duplicate insertions via
  `insertIfNotExists`.

It also has two integrity gaps:

1. **Physical proof deletion is exposed.** The
   `ProofVaultController` exposes endpoints that physically
   `DELETE` proof rows. If the vault API is reachable by a
   malicious actor (e.g. via a compromised internal service
   account, a misrouted public ingress, or a CSRF/SSRF on a
   neighbouring service), deletion can remove double-spend
   evidence. Once a spent proof's row is gone, the mint can
   be persuaded by a forged audit to treat the same proof as
   re-spendable — a classic deflation/inflation hybrid risk.

2. **Proof retrieval by `secret` is global, not mint-scoped.**
   `ProofRepository.java:122` looks up a proof by `secret`
   without requiring `mint_id`. Cashu protocol allows two
   different mints to legitimately register the same secret
   for two different proofs. A global lookup will return the
   first match, which may be the wrong proof — leading to
   wrong-mint state decisions, mis-attributed double-spend
   detection, and incorrect state checks.

This spec closes both gaps and codifies the mint-scoped
contract throughout the controller and repository layers.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Spent proofs are evidence; they cannot be physically removed (Priority: P1)

A vault operator MUST be able to trust that no spent or
otherwise-state-transitioned proof can be physically deleted
through any code path reachable from the REST API or service
layer. "Deletion" is replaced by a privileged, audited
tombstone that retains the original row's `mint_id`,
`secret`, `c`, and state history.

**Why this priority**: This is the existential
proof-state-integrity concern. Today, a successful call
against the delete endpoint can erase double-spend evidence
permanently. Direct violation of Constitution Principle I.

**Independent Test**: An integration test (Testcontainers
PostgreSQL) that inserts a proof, transitions it to `SPENT`,
calls the delete endpoint with admin credentials, and asserts
the row remains present with `tombstoned_at` populated; calls
without admin credentials return 403 and leave the row
untouched.

**Acceptance Scenarios**:

1. **Given** a proof in `SPENT`, **When** any non-admin
   principal calls a deletion endpoint, **Then** the request
   is rejected with 401 / 403, no SQL `DELETE` is issued,
   and the attempt is logged with structured fields.
2. **Given** the same proof, **When** an admin principal
   issues a deletion request, **Then** the row is **not**
   physically removed; instead a `tombstoned_at` timestamp
   and `tombstoned_by` principal id are written, the
   Envers audit row is created, and a security event is
   logged.
3. **Given** a tombstoned proof, **When** the mint queries
   it via the standard read endpoint, **Then** the response
   indicates the proof is tombstoned (state-check returns
   `SPENT` plus a tombstoned flag; the proof is still
   considered spent for double-spend prevention).
4. **Given** a proof in `UNSPENT`, **When** an admin
   principal attempts a tombstone, **Then** the request is
   rejected unless an explicit operator override flag is
   present (UNSPENT proofs are not tombstoned in normal
   operation).
5. **Given** any tombstone action, **When** the action
   completes, **Then** an append-only audit row records
   `(mint_id, secret, c, tombstoned_at, tombstoned_by,
   reason)`.

---

### User Story 2 — Every proof lookup is mint-scoped (Priority: P1)

A vault operator MUST be able to trust that no code path
returns a proof for a `secret` without simultaneously
matching on `mint_id`. Global secret lookup is removed
from the repository and controller layer.

**Why this priority**: Two different mints can legitimately
hold proofs with the same `secret`. Today, a global lookup
returns the first match — which can be the wrong proof. This
silently corrupts state-check results and double-spend
decisions. Direct violation of Constitution Principle I
("mint-scoped lookup").

**Independent Test**: An integration test that inserts two
proofs with the same `secret` under two different `mint_id`s,
then queries via `(mint_id_A, secret)` and `(mint_id_B,
secret)` and asserts each call returns the correct proof.
Verify the repository no longer exposes a `findBy(secret)`
overload that omits `mint_id`.

**Acceptance Scenarios**:

1. **Given** two proofs with identical `secret` under
   different `mint_id`s, **When** the mint queries with
   `(mint_id_A, secret)`, **Then** only the proof belonging
   to `mint_id_A` is returned.
2. **Given** the same setup, **When** the mint queries with
   `(mint_id_B, secret)`, **Then** only the proof belonging
   to `mint_id_B` is returned.
3. **Given** any caller, **When** it attempts to use a
   legacy global-secret lookup endpoint (if any was
   advertised), **Then** the call is rejected with 400 and
   a deprecation-removal message.
4. **Given** a state-check request that omits `mint_id`,
   **When** received, **Then** the request is rejected
   with 400 before the database is touched.
5. **Given** the repository surface, **When** inspected,
   **Then** no public method returns a proof by
   `secret`-only or `c`-only without a `mint_id` operand.

---

### User Story 3 — Tombstone and lookup operations are auditable (Priority: P2)

Every tombstone, every mint-scope violation rejection, and
every cross-mint lookup attempt MUST be observable post-hoc
via Hibernate Envers audit rows and structured logs.

**Why this priority**: Useful but not blocking; depends on
Stories 1 and 2 landing. Constitution Principle II
mandates the audit trail.

**Independent Test**: A read endpoint (admin-only) returns
the tombstone timeline and the audit trail for a given
`(mint_id, secret)`.

**Acceptance Scenarios**:

1. **Given** a tombstoned proof, **When** an admin queries
   its audit history, **Then** the response includes the
   `tombstoned_at`, `tombstoned_by`, the prior state
   transitions, and the Envers revisions.
2. **Given** an attempted mint-scope violation, **When**
   logged, **Then** the structured log entry includes
   `(requested_mint_id, lookup_secret_prefix, principal_id,
   outcome=scope_violation)`.

---

### Edge Cases

- A proof is inserted via `insertIfNotExists` and the
  existing row has a different `mint_id` (impossible under
  the unique constraint, but defence-in-depth: the catch
  block MUST verify the existing row matches before
  treating the insert as idempotent success).
- An admin attempts to tombstone a proof in `UNSPENT`
  (must require an explicit override flag and log heavily).
- A controller method accepts `mint_id` from the query
  string but the underlying service uses the principal's
  scoping token (the service-level scoping MUST win;
  mismatch MUST be rejected).
- A query arrives during a Flyway migration that adds the
  `tombstoned_at` column (online migration safety — covered
  by planning, not in scope here, but the spec MUST be
  compatible with online migration).
- Two concurrent insert attempts for the same `(mint_id,
  secret)` (must collapse via `insertIfNotExists`; only one
  row exists, no errors propagate beyond the catch).
- A legacy caller that has cached a `secret`-only lookup
  URL hits the new mint-scoped controller (must receive
  400, not 404, so client logs distinguish migration
  problems from data-not-found).
- Hibernate Envers `_AUD` table rows are never edited or
  deleted by application code, even during tombstone
  operations.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Physical `DELETE` of `ProofEntity` rows MUST
  be removed from all controller and service code paths.
  Repository-level `delete*` methods MUST either be removed
  or restricted to a package-private migration helper not
  reachable from any controller. [Constitution I]
- **FR-002**: The vault MUST introduce a `tombstoned_at`
  (timestamp, nullable) and `tombstoned_by` (principal id,
  nullable) on `ProofEntity`. Tombstoning MUST be the only
  externally-observable form of "deletion".
- **FR-003**: Tombstoning MUST be executed by an admin-only
  service operation. Non-admin principals MUST receive 403
  before any database access. [Constitution Security]
- **FR-004**: The existing `(mint_id, secret)` and
  `(mint_id, c)` unique constraints on `ProofEntity` MUST
  be retained verbatim. Migrations MUST NOT relax them.
  [Constitution I — mint-scoped uniqueness]
- **FR-005**: All `ProofRepository` lookup methods that
  return `ProofEntity` MUST require `mint_id` as a
  parameter alongside `secret` or `c`. Public methods
  returning a proof by `secret`-only or `c`-only MUST be
  removed (or marked `@Deprecated` with a removal target
  in the same release).
- **FR-006**: All `ProofVaultController` endpoints that
  retrieve or transition proof state MUST require
  `mint_id` in the request (path, query, or body) AND
  MUST cross-check `mint_id` against the authenticated
  principal's scope. Mismatch MUST be rejected with 403.
- **FR-007**: A request that omits `mint_id` on any read,
  write, or state-transition endpoint MUST be rejected
  with HTTP 400 before any database access.
- **FR-008**: No `UPDATE` may change a proof's `secret`,
  `c`, or `mint_id`. Repository methods that could
  produce such an update MUST be removed; service-level
  updates MUST target only state-machine columns.
  [Constitution I — no silent overwrite]
- **FR-009**: Every proof creation MUST go through
  `insertIfNotExists`. The catch-block MUST verify that
  the existing row's `(mint_id, secret, c)` matches the
  proposed insertion before treating the conflict as
  idempotent success; mismatch MUST be rejected as a
  data-integrity violation and surfaced as an operator
  alert via a structured log line with
  `event=proof_insert_mismatch outcome=integrity_mismatch
  severity=high` plus a SIEM rule wired to this
  event/outcome pair. The same channel applies to the
  id-collision pre-check variant
  (`reason=id_collision`). [Constitution I]
- **FR-010**: Hibernate Envers MUST capture every state
  transition on `ProofEntity`, including tombstoning.
  Schema changes that add audited columns MUST update
  the corresponding `_AUD` migration in lock-step.
- **FR-011**: Every rejected mutation (unauthorised
  delete, scope violation, missing `mint_id`,
  data-integrity mismatch) MUST be logged with structured
  fields suitable for SIEM ingestion. Concretely, every
  rejection log line MUST carry at minimum:
  `event=<event-name> outcome=<code> principal=<auth.name
  or "anonymous"> path=<request path> method=<HTTP verb>`,
  plus event-specific fields. For scope violations the
  four spec-mandated fields are:
  `outcome=scope_violation requested_mint_id=<uuid>
  lookup_secret_prefix=<first 6 chars> principal_id=<auth
  .name>`. The log channel MUST be wired to the operator
  SIEM with alert rules on `outcome ∈ {scope_violation,
  integrity_mismatch, tombstone_attempt_rejected}`.
- **FR-012**: The live `t_proof.tombstoned_at` value
  MUST come from the database (`now()`), not the JVM
  clock. The live `t_proof.updated_at` value, when
  written by a state-transition (`POST .../state`),
  MUST come from the database (`now()`) — implemented
  via the `updateState` native query in
  `ProofRepository`. Hibernate Envers audit-row
  timestamps (`t_proof_a.tombstoned_at`,
  `t_proof_a.updated_at`, and the `revinfo.revtstmp`
  revision timestamp) capture the entity state at JPA
  flush time and are sub-second JVM-clock
  approximations of the canonical live values — this
  is acceptable since the audit row is forensic
  evidence of the operation, not the authoritative
  source of truth (the live row is). The
  `ProofTombstoneIT.tombstonedAtIsDbClockOnLiveRow`
  test brackets the tombstone call with two `SELECT
  now()` samples and asserts the live row's
  `tombstoned_at` falls inside the window. [Constitution
  II — audit clock; live-row strict, audit-row
  approximation documented]
- **FR-013**: An admin-only audit endpoint MUST return
  the Envers timeline and tombstone metadata for a
  given `(mint_id, secret)`. The endpoint MUST require
  admin authentication.
- **FR-014**: The vault REST API MUST NOT be exposed on
  a public network; documentation MUST explicitly state
  this requirement, and deployment manifests MUST
  enforce it (network policy, service-mesh mTLS, private
  subnet — at least one MUST be in place; SHOULD-strength
  combination is preferred). [Constitution Security]
- **FR-015**: All amount-bearing fields (if any are
  introduced by this spec or related migrations) MUST
  use `long`; this spec does not introduce new
  amount-bearing fields but reaffirms the constraint
  for any future migration.

### Key Entities

- **ProofEntity (extended)**: existing entity with the
  new nullable fields `tombstoned_at`,
  `tombstoned_by`, plus an existing-by-construction
  `version` column. Existing `(mint_id, secret)` and
  `(mint_id, c)` constraints retained.
- **ProofAudit (Envers `_AUD` view)**: existing
  Envers-managed audit table; extended to track the new
  tombstone fields.
- **TombstoneEvent (optional, structured-log only)**:
  not a JPA entity. Emitted as a structured log line on
  every tombstone for SIEM ingestion. If durable
  capture is desired, an `OperatorAction` table can be
  added; out of scope for this spec.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 0 controller / service paths issue SQL
  `DELETE` against `ProofEntity` in production.
  (Verified by code review + repository static analysis.)
- **SC-002**: 0 `ProofRepository` public methods accept
  `secret`-only or `c`-only without `mint_id`.
  (Verified by compile-time inspection.)
- **SC-003**: 100% of state-check requests against a
  cross-mint duplicate-secret pair return the correct
  proof (integration test in `cashu-vault-api` with
  Testcontainers PostgreSQL).
- **SC-004**: 100% of tombstone actions produce both an
  Envers audit row AND a structured-log event.
  (Verified by integration test that asserts on both.)
- **SC-005**: 100% of unauthenticated delete / tombstone
  requests in `staging` and `prod` return 401 / 403
  before any database access. (Smoke test in CI.)
- **SC-006**: 0 successful unauthorised tombstones in
  production logs after rollout. (Operator dashboard
  alerts on `outcome=tombstone_attempt_rejected` with a
  non-admin principal.)

## Assumptions

- Hibernate Envers is already configured in
  `cashu-vault-jpa` (the constitution names it as the
  canonical audit mechanism); this spec extends the
  existing setup rather than introducing it.
- Flyway migrations to add `tombstoned_at` /
  `tombstoned_by` are online-safe; the operator's
  deployment process can apply them with brief table
  locks during low-traffic windows.
- The mint (cashu-mint) is the primary consumer; its
  callers already pass `mint_id` in most paths. Where
  they don't, a coordinated change is tracked separately
  but assumed deliverable in lock-step.
- Admin authentication is service-account-based and is
  **introduced by this spec** via Spring Security HTTP
  Basic with property-driven user accounts. Each user
  carries `ROLE_<role>` granted authorities and
  (for `ROLE_SERVICE` accounts) a `MINT:<mintScope>`
  granted authority used by the controller-layer
  cross-check (FR-006). Production credentials MUST be
  sourced from environment variables or HashiCorp
  Vault. The earlier draft of this Assumption stated
  auth was already integrated — that was factually
  incorrect; corrected on 2026-05-24 per
  `/speckit.analyze` finding A1. See `research.md` §1
  and `plan.md` for the resolved decision (CL-01).
- The Spring Data REST surface (if any was inadvertently
  exposing `ProofRepository`) MUST be disabled as part
  of this spec; the controller path is the only
  supported integration point.
- HashiCorp Vault (`cashu-vault-hashi` module) is out of
  scope for this spec; it manages keyset material, not
  proof state.
