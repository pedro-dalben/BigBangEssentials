# Jobs Module Migration Guide

## Overview

This document covers migration from the pre-refactor Jobs codebase to the current system (v1.0.2.6+build.927).

## Old Code → New Code

| Aspect | Old (Pre-Refactor) | New (Current) |
|--------|-------------------|---------------|
| **Profession count** | 10 hardcoded | 17 configurable via JSON |
| **Config files** | None (hardcoded in Java) | `global.json`, `slots.json`, `milestones.json`, 17 × `professions/*.json` |
| **Schema version** | N/A | 2 |
| **Display names** | "Test" (placeholder) | Real Portuguese names |
| **XP curve** | Hardcoded `level * 100` | Configurable `xp-curve` (polynomial or linear) |
| **Daily limits** | None | Configurable global + per-job caps |
| **Slots** | No slot system (just max-active-jobs) | 3 slot types with milestone unlock |
| **Licenses** | No license system | License quest with objectives |
| **Skills** | No skill system | Configurable skill trees |
| **Contracts** | None | Daily/weekly contracts |
| **Integrations** | Direct imports | Reflective bridges with health states |
| **Anti-exploit** | Minimal | Full pipeline: provenance, cooldown, rate limit, dedup |
| **Builder pattern** | Constructor spaghetti | `JobsConfig.Builder`, `JobDefinition.Builder` |
| **Permissions** | `jobs.*` prefix | `bigbangessentials.jobs.*` with legacy aliases |
| **Menus** | Basic inventory GUI | YAML-driven menus with lore/hover data |
| **Database** | Single table | 7 repositories with proper schema |
| **Events** | None | 8 NeoForge events (cancellable where appropriate) |
| **Tests** | 0 | 54 tests covering pipeline, integration, anti-exploit |

## Config Path Migration

### Automatic (on server start)

`JobsConfigLoader.migrateIfNeeded()` runs on every server start:

1. **Check**: Does `config/bigbangessentials/jobs/` exist?
2. **Backup canonical**: If `world/serverconfig/bigbangessentials/jobs/` already exists, copy to `world/serverconfig/bigbangessentials/jobs_backup_<timestamp>/`
3. **Copy**: Move all files from legacy to canonical path
4. **Mark**: Write `.migrated` file in legacy dir with content `migrated_to=<canonical_path>`
5. **Continue**: Load from canonical path

### Manual Migration
```bash
# If automatic migration failed or was skipped:
cp -r config/bigbangessentials/jobs/ world/serverconfig/bigbangessentials/jobs/
echo "migrated_to=$(pwd)/world/serverconfig/bigbangessentials/jobs" > config/bigbangessentials/jobs/.migrated
```

## Permission Node Migration

### Old → New Mapping

| Old Node | New Node |
|----------|----------|
| `jobs.command.jobs` | `bigbangessentials.jobs.command.menu` |
| `jobs.command.list` | `bigbangessentials.jobs.command.list` |
| `jobs.command.entrar` | `bigbangessentials.jobs.command.join` |
| `jobs.command.sair` | `bigbangessentials.jobs.command.leave` |
| `jobs.command.info` | `bigbangessentials.jobs.command.info` |
| `jobs.command.ganhos` | `bigbangessentials.jobs.command.earnings` |
| `jobs.command.habilidades` | `bigbangessentials.jobs.command.skills` |
| `jobs.command.top` | `bigbangessentials.jobs.command.top` |
| `jobs.command.license` | `bigbangessentials.jobs.command.license` |
| `jobs.command.slot` | `bigbangessentials.jobs.command.slot` |
| `jobs.ganhos.*` | `bigbangessentials.jobs.bonus.earnings` |
| `jobs.xp.*` | `bigbangessentials.jobs.bonus.xp` |
| `jobs.limitediario.*` | `bigbangessentials.jobs.bonus.dailylimit` |
| `jobs.limite.*` | `bigbangessentials.jobs.bonus.slots` |
| `jobs.admin.*` | `bigbangessentials.jobs.admin` |
| `jobs.profissao.*` | `bigbangessentials.jobs.profession` |

### Dual-Check Behavior

The permission system checks both nodes. If a player has the legacy node, it is treated as equivalent to the canonical one. This is configured in `global.json`:

```json
"permissions": {
  "prefix": "bigbangessentials.jobs",
  "legacy-aliases": {
    "jobs.command.jobs": "bigbangessentials.jobs.command.menu",
    ...
  }
}
```

### LuckyPerms Migration

Run these commands to migrate player/group permissions:

```bash
# Bulk migrate all groups
lp group default permission set bigbangessentials.jobs.command.menu true
lp group default permission unset jobs.command.jobs
# ... repeat for all nodes

# Or keep both (dual-check handles it automatically)
```

**Recommendation**: Keep legacy nodes during transition period, then remove after verifying all players work correctly.

## Player Data Migration

### Legacy Active Jobs → Slot System

Old system: `bbe_player_jobs` table had `active` boolean column. Players could toggle jobs on/off.

New system: Active jobs are managed through slots (`bbe_player_job_slots` table). Slots are unlocked via rank milestones.

**Migration steps**:

1. **Identify active jobs**: Query `bbe_player_jobs` where `active = true`
2. **Apply slot assignments**: For each player with active jobs:
   - First active job → `COMMON_PRIMARY` slot
   - Second active job → `COMMON_SECONDARY` slot
3. **Insert into `bbe_player_job_slots`**: `INSERT INTO bbe_player_job_slots (player_uuid, job_id, slot_type) VALUES (?, ?, ?)`

### License Grants

New system requires licenses for Pokemon specializations. For migrating existing players:

1. **Check**: Did player ever have levels in a Pokemon job? If level > 1, grant permanent license
2. **Insert**: `INSERT INTO bbe_job_licenses (player_uuid, job_id, granted_at) VALUES (?, ?, ?)`

### Command
```
/jobsadmin migrate
```

Runs data migration routines. Requires console permission (`bigbangessentials.jobs.admin.migrate`).

**What it does**:
1. Scans `bbe_player_jobs` for active jobs
2. Creates slot assignments for each active job
3. Grants permanent licenses for Pokemon jobs with level > 1
4. Logs migration summary to console

## Config File Migration

### Pre-Refactor: No Config Files

Old code had all profession definitions hardcoded in Java. No external config existed.

### Post-Refactor: Auto-Generated

On first server start, `JobsConfigLoader` detects empty `professions/` directory and generates all 17 default JSON files. These files can be edited and will persist across reloads.

**Missing files regenerated**: If a profession JSON is deleted, the loader creates a fresh default for that profession on next load. Other existing files are preserved.

### Config Hierarchy Change

| Old | New |
|-----|-----|
| `config/bigbangessentials/jobs/` | `world/serverconfig/bigbangessentials/jobs/` |
| Single `jobs.json` (hypothetical) | Directory structure with multiple files |
| No subdirectories | `professions/` subdirectory |

## XP Data Migration

Old XP curve was `level * 100`. Current default is polynomial `base × level^exponent × multiplier` where defaults are `100 × level^1.5 × 1.0`.

**Impact**: Players at the same level will have the same level in the new system, but their accumulated XP may differ from what it would have been under the old curve. This is cosmetic — level is preserved.

**No action needed**: XP values are stored, not recomputed. Levels are preserved as-is.

## Rollback Procedure

### If Migration Fails

1. **Restore config backup**:
   ```bash
   cp -r world/serverconfig/bigbangessentials/jobs_backup_<timestamp>/* world/serverconfig/bigbangessentials/jobs/
   ```

2. **Restore database**: If database migration ran, restore `bbe.db` from pre-migration backup

3. **Remove migration marker**:
   ```bash
   rm -f config/bigbangessentials/jobs/.migrated
   ```

4. **Start server** — will load from canonical path, no migration triggered

### If New System Has Issues

1. **Stop server**
2. **Restore config** from backup
3. **Restore database** from backup
4. **Remove `.migrated`** marker file
5. **Restart** with old codebase if needed

## Verification Checklist

After migration, verify:

- [ ] 17 profession JSONs exist in `world/serverconfig/bigbangessentials/jobs/professions/`
- [ ] `global.json` has `schema-version: 2`
- [ ] `slots.json` has 3 slot definitions
- [ ] `milestones.json` has 3 milestones
- [ ] `/jobsadmin diag` shows 17 professions, 3 slots
- [ ] `/jobsadmin integrations probe` shows correct bridge states
- [ ] Players can join professions via `/jobs entrar <id>`
- [ ] Player levels match pre-migration values
- [ ] Legacy permission nodes still work (dual-check period)
- [ ] No "Test" display names appear in menus
