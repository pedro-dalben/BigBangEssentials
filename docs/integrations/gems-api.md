# Public Gems integration API

The supported cross-mod contract is `BigBangEssentialsApi.gemsIntegration()` and the types in `com.pedrodalben.bigbangessentials.api.gems`.

`GemsProviderSnapshot` separates configured, enabled, database-ready, capabilities, and provider state. A provider is usable only in `READY`. `WAITING_FOR_DATABASE` and `TEMPORARILY_UNAVAILABLE` are retryable; `DISABLED`, `FAILED`, and `SHUTTING_DOWN` must be surfaced to the caller.

`reserveAsync`, `renewAsync`, `captureAsync`, `releaseAsync`, `balanceAsync`, and reservation lookup return `CompletableFuture`. Implementations may use the database executor, but callers must never block the Minecraft server thread. Requests require bounded source/purpose identifiers and stable idempotency keys. A repeated key with the same fingerprint returns the original result; a different fingerprint returns `IDEMPOTENCY_CONFLICT`.

The API version remains `1`: this is an additive, source-compatible facade. Consumers must not import `GemsManager`, `DatabaseGemsService`, persistence classes, or internal database configuration.

For MySQL 8 deployments, the bundled Connector/J configuration explicitly permits public-key retrieval for non-TLS authentication. Production deployments should still use verified TLS where available.
