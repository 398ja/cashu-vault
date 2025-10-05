title: chore: Bump cashu-lib to 0.4.1 and adjust workflows

## Summary
Related issue: #____
Upgrade Cashu library dependencies to `0.4.1` across modules via the root Maven property. This keeps the project aligned with the latest Cashu library release. No application code or API behavior changes are included.
Additionally tightens GitHub workflow branch targets for Qodana and code formatting to reduce noise.

## What changed?
- Bump property `cashu-lib.version` from `0.4.0` to `0.4.1` (F:pom.xml†L43)
- Modules consume the updated version through the parent property:
  - `cashu-vault-api` uses `${cashu-lib.version}` for `cashu-lib-common` (F:cashu-vault-api/pom.xml†L44)
  - `cashu-vault-jpa` uses `${cashu-lib.version}` for `cashu-lib-common` (F:cashu-vault-jpa/pom.xml†L71)
- CI/Workflows:
  - Qodana runs only on `main`, `master`, and `develop` for pushes and PRs (F:.github/workflows/code_quality.yml†L1-L12)
  - Google Java Format runs only on PRs to `main` or `master` (F:.github/workflows/google-java-format.yml†L3-L8)

## BREAKING
None. Dependency patch update only; no API or persistence changes.

## Protocol Compliance
- No change to protocol semantics or endpoints. Behavior remains compliant with Cashu NUTs (see https://github.com/cashubtc/nuts/blob/main/00.md and related NUTs).

## Testing
- ✅ `./mvnw -q -v`
  3.8.6
- ❌ `./mvnw -q verify`
  Tests fail in this environment due to Mockito inline mock-maker requiring agent attach on the current JDK. Representative output:
  - Tests run: 6, Failures: 0, Errors: 6, Skipped: 0
  - Mockito initialization/ByteBuddy self-attach error on JDK 23

Notes to unblock locally/CI:
- Run with JDK 21 (preferred target), or
- Add Mockito as `-javaagent` per Mockito docs, or
- Use `mockito-inline` with `-Djdk.attach.allowAttachSelf=true` (not ideal for CI)

## Network Access
- No external network access observed during `verify` (local dependency cache used).

## Checklist
- [x] Title uses `type: description`
- [x] File citations included
- [x] No functional changes; protocol compliance unchanged
- [x] Tests executed; environment limitation noted with remediation
- [x] Workflows updated with correct branch filters
