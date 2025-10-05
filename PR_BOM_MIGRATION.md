title: feat: migrate to cashu-platform-bom for centralized version management

## Summary
Related issue: #____
Migrate cashu-vault to use `cashu-platform-bom` for centralized dependency version management across the Cashu ecosystem. This eliminates duplicate version properties and ensures consistent dependency versions.

## What changed?
- **Version bump**: `0.2.4` → `0.2.5`
- Replace all version properties with single `cashu-platform-bom.version` property (F:pom.xml†L24)
- Import `cashu-platform-bom:1.0.0` in `dependencyManagement` (F:pom.xml†L30-L36)
- Remove version tags from all dependencies in child modules:
  - `cashu-vault-jpa`: removed versions for cashu-lib-common, postgresql, h2, flyway, hibernate-envers (F:cashu-vault-jpa/pom.xml†L70-L87)
  - `cashu-vault-api`: removed version for cashu-lib-common (F:cashu-vault-api/pom.xml†L44)
- Simplify plugin management - versions now inherited from BOM (F:pom.xml†L60-L112)
- Fix hibernate-envers groupId in BOM from `org.hibernate.orm` to `org.hibernate` and redeploy BOM

## Benefits
- **Single source of truth**: All Cashu ecosystem versions managed in one place
- **Consistency**: Identical dependency versions across all Cashu projects
- **Simplified updates**: Bump cashu-lib version once in BOM, all projects inherit it
- **Reduced duplication**: From 40+ version properties to 1

## Architecture
```
cashu-vault (this project)
  └─ imports cashu-platform-bom
       ├─ imports nostr-java-bom (shared: Jackson, Lombok, BouncyCastle, test deps)
       ├─ imports spring-boot-dependencies
       ├─ defines all Cashu module versions
       └─ defines Cashu-specific dependencies
```

## BREAKING
None. Internal build configuration change only; no API or runtime behavior changes.

## Protocol Compliance
- No change to protocol semantics or endpoints. Behavior remains compliant with Cashu NUTs (see https://github.com/cashubtc/nuts/blob/main/00.md and related NUTs).

## Testing
- ✅ `mvn clean compile -U` - BUILD SUCCESS
- All modules compile successfully with BOM-managed versions
- No dependency resolution errors

## Checklist
- [x] Title uses `type: description`
- [x] File citations included
- [x] Version bumped to 0.2.5
- [x] Build verified with BOM
- [x] No functional changes; protocol compliance unchanged
- [x] BOM deployed to https://maven.398ja.xyz/releases/xyz/tcheeric/cashu-platform-bom/1.0.0/
