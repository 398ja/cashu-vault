# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/398ja/cashu-vault/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/398ja/cashu-vault/compare/v0.4.6...v0.5.0
[0.4.6]: https://github.com/398ja/cashu-vault/compare/v0.4.5...v0.4.6
[0.4.5]: https://github.com/398ja/cashu-vault/compare/v0.4.4...v0.4.5
[0.4.4]: https://github.com/398ja/cashu-vault/compare/v0.4.3...v0.4.4
[0.4.3]: https://github.com/398ja/cashu-vault/compare/v0.4.1...v0.4.3
[0.4.1]: https://github.com/398ja/cashu-vault/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/398ja/cashu-vault/releases/tag/v0.4.0
