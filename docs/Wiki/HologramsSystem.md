# Holograms System

## Overview

`BigBangHolograms` is the shared hologram module for BigBangEssentials.

The default renderer is `CLIENT_ONLY_TEXT_DISPLAY`:

- no `ArmorStand` renderer for new holograms
- no persistent hologram entities saved in chunks
- one multiline virtual `TextDisplay` per hologram
- visibility tracked per player

## Public API

Entry point:

```java
HologramService api = BigBangHolograms.getApi();
```

Create or update:

```java
api.createOrUpdate(
    HologramDefinition.builder("bigbangessentials:spawn/rules")
        .ownerId("bigbangessentials:admin")
        .location(new HologramLocation(Level.OVERWORLD, 0.5D, 80.0D, 0.5D))
        .lines(List.of("&6Regras", "&7Leia antes de jogar"))
        .viewDistance(24)
        .visibilityPolicy(HologramVisibilityPolicy.NEARBY_PLAYERS)
        .persistent(true)
        .build()
);
```

Supported operations:

- `find`
- `findDefinition`
- `exists`
- `create`
- `createOrUpdate`
- `update`
- `delete`
- `deleteByOwner`
- `showTo`
- `hideFrom`
- `reload`
- `shutdown`
- `getStats`

## Crates Migration

Crate holograms now use stable IDs:

```text
bigbangessentials:crate/<location-uuid>
```

The crate adapter delegates to `BigBangHolograms` and no longer treats `ArmorStand` as the active renderer path.

## Legacy Cleanup

Legacy crate holograms are recognized only by these tags:

- `crate_hologram`
- `crate_hologram_<uuid>`

Cleanup entry points:

- `/crates location cleanup`
- `/crate location cleanup`
- `/hologram cleanup legacy`

These commands use the same cleaner and do not touch virtual holograms.

## Commands

Available admin commands:

- `/hologram list`
- `/hologram inspect <id>`
- `/hologram create <id>`
- `/hologram remove <id>`
- `/hologram move <id>`
- `/hologram setline <id> <line> <text>`
- `/hologram addline <id> <text>`
- `/hologram removeline <id> <line>`
- `/hologram setdistance <id> <blocks>`
- `/hologram setoffset <id> <x> <y> <z>`
- `/hologram page add <id>`
- `/hologram page remove <id> <page>`
- `/hologram page setinterval <id> <ticks>`
- `/hologram visibility <id> <mode>`
- `/hologram reload`
- `/hologram stats`
- `/hologram cleanup legacy`

Permissions:

- `bigbangessentials.holograms.admin`
- `bigbangessentials.holograms.create`
- `bigbangessentials.holograms.edit`
- `bigbangessentials.holograms.remove`
- `bigbangessentials.holograms.reload`
- `bigbangessentials.holograms.cleanup`
- `bigbangessentials.holograms.stats`

## Performance Model

The service uses:

- chunk-based spatial indexing
- per-player visible hologram caches
- scheduled content updates instead of full per-tick scans
- client-only spawn/update/destroy packets

## Current Notes

This implementation is centered on the shared runtime, crate migration path, legacy cleanup, and the admin command surface. The renderer sends client-only `TextDisplay` metadata and does not persist hologram entities in the world.
