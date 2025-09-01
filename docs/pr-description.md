Title: fix: Provide default server.port and remove empty server.address

## Why now?
The new configuration set `server.port=` and `server.address=` to empty strings. On startup without overrides, Spring Boot fails to bind `server.port` to `java.lang.Integer`, preventing the service from starting. Previously, `server.port` defaulted to a valid value so the app booted out-of-the-box. This change restores a safe default to keep local runs and tests working.

## What changed?
- F:cashu-vault-jpa/src/main/resources/application.properties†L16-L20 — set `server.port=${cashu_vault_port:8080}` and removed empty `server.address` assignment; kept `vault.base.url` in sync with the resolved port.
- F:pom.xml†L7 — bumped root version to `0.2.0` per branch rule.
- F:cashu-vault-api/pom.xml†L7 — bumped module version to `0.2.0`.
- F:cashu-vault-jpa/pom.xml†L8,L12 — bumped parent and module version to `0.2.0`.

## BREAKING
None. Default port now resolves to `8080` if not provided; existing deployments can still override via `cashu_vault_port` or `server.port`.

## Review focus
- Confirm defaulting via `${cashu_vault_port:8080}` aligns with your deployment conventions.
- Validate `vault.base.url` usage remains correct with the resolved port.

## Testing
- ✅ `./mvnw -q verify`
  - Spring Boot context starts, JPA/H2 initialize, tests pass. Excerpt:
  - `:: Spring Boot :: (v3.5.0)` and `Started CashuVaultApplicationTests` observed.
  - Full output saved at `docs/verify-output.txt`.

## Network Access
- Accessed `repo.maven.apache.org` to resolve Maven Wrapper and dependencies during `verify`.

## Notes
- No code paths changed beyond configuration; behavior remains compliant with Spring Boot defaults.
