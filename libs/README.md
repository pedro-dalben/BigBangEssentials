# Cobblemon development artifacts

Cobblemon is no longer stored in `libs/`. Gradle resolves compile-time artifacts
from the Modrinth Maven repository (`https://api.modrinth.com/maven`) using
loader-specific version IDs defined in `gradle.properties`:

| Loader | Modrinth Version ID | Gradle property |
|---|---|---|
| Fabric | `kF7CvxTo` | `cobblemon_fabric_version_id` |
| NeoForge | `S1TrAn8c` | `cobblemon_neoforge_version_id` |

Both artifacts are `compileOnly`/`modCompileOnly` and are not bundled into the
BigBangEssentials output JARs. The server provides Cobblemon separately at
runtime.
