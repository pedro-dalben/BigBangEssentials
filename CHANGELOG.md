# Changelog

All notable changes to the BigBangEssentials project will be documented in this file.

## [1.0.2.6] - 2026-06-27

### Added
- **Gems Wallet Crash Injection framework:** Injected `checkFailpoint(...)` hooks throughout transaction phases.
- **GemCrashInjectionTest:** Integrated a suite of JUnit tests utilizing failpoints to assert correct recovery behaviors.
- **Gems Concurrency validation:** Expanded `GemReservationConcurrencyTest` to verify concurrent capture/release, concurrent multiple captures, and concurrent multiple releases.

### Changed
- **Copy-on-Write (CoW) Persistence Pattern:** Refactored state mutations in `GemsManager` to utilize a Copy-on-Write clone-and-swap pattern, eliminating any possibility of memory/disk cache divergence during write failures.
- **Brigadier Admin Commands safety:** Required explicit literal `"confirm"` suffix on manual release commands to prevent accidental operator clicks.
- **Idempotency Key Conflict handling:** Strict payload validation check added for duplicate idempotency key requests, returning `IDEMPOTENCY_CONFLICT` on mismatched payloads.

### Fixed
- Cache/Disk state divergence in Gems Manager transactions.
- Lack of confirmation for manual reservation release.
- Missing parameter count on concurrent release requests.
