# Jobs Menu System — Architecture & Reference

## Architecture Overview

The Jobs Menu System replaces the legacy text-based `/jobs` output with a configurable YAML-driven GUI. It's built on three layers:

1. **Availability Layer** (`JobAvailabilityService`) — evaluates player eligibility per job, producing a `JobAvailabilityResult` with status, requirements, and visibility.
2. **ViewModel Layer** (`JobMenuViewModel`, `JobMenuViewModelFactory`) — transforms raw domain data into presentation-ready records consumable by the menu renderer.
3. **Menu Integration** (`JobsMenuSupport`) — bridges the menu system (`MenuSystem`) to jobs domain, providing placeholder values and filter/sort utilities.

### Data Flow

```
Player Action → JobsCommand (executeSummary)
  → MenuSystem.openMenu("jobs_menu")
    → JobsMenuSupport.buildJobPlaceholders(player, job)
      → JobAvailabilityService.evaluate(player, job)
      → JobMenuViewModelFactory.create(player, job)
    → YAML template renders slots with resolved placeholders
```

## Availability States

| Status | Can Join | Can Leave | Visible | Description |
|--------|----------|-----------|---------|-------------|
| `ACTIVE` | No | Yes | Yes | Player currently working this job |
| `AVAILABLE` | Yes | Yes | Yes | All requirements met, ready to join |
| `LOCKED` | No | Yes | Conditional | General unmet requirements |
| `LICENSE_REQUIRED` | No | Yes | Yes | Must complete license quest first |
| `RANK_REQUIRED` | No | Yes | Yes | Player rank too low |
| `PERMISSION_REQUIRED` | No | Yes | Yes | Missing permission node |
| `NO_AVAILABLE_SLOT` | No | Yes | Yes | All slots full or no matching category |
| `COOLDOWN` | No | Yes | Yes | Slot cooldown not expired |
| `INTEGRATION_UNAVAILABLE` | No | Yes | Conditional | Required mod/plugin not active |
| `CONFIGURATION_ERROR` | No | No | False | Job definition broken or missing |
| `ADMIN_DISABLED` | No | No | True | Disabled by admin via config |

## Configuration Guide

### Visibility Modes (`visibility` block in job YAML)

```yaml
visibility:
  mode: ALWAYS_VISIBLE          # ALWAYS_VISIBLE | VISIBLE_WHEN_DISCOVERED | HIDDEN_WHEN_UNAVAILABLE
  showRequirementsWhenLocked: true
  allowPreview: true
```

| Mode | Behavior |
|------|----------|
| `ALWAYS_VISIBLE` | Job always shows in the menu list |
| `VISIBLE_WHEN_DISCOVERED` | Hidden until player discovers the job (via `JobDiscoveryService`) |
| `HIDDEN_WHEN_UNAVAILABLE` | Hidden when status is not ACTIVE or AVAILABLE |

### Visual States (item lore/name)

The `JobsMenuSupport.buildJobPlaceholders` method injects these placeholders:

| Placeholder | Source | Example |
|-------------|--------|---------|
| `{job_status_color}` | Availability result | `<green>` (ACTIVE), `<red>` (LOCKED) |
| `{job_status_key}` | Status enum lowercase | `active`, `locked` |
| `{job_status}` | Status text from ViewModel | `Ativo`, `Bloqueado` |
| `{job_license_label}` | License + availability | `Disponível para entrar` |
| `{job_can_join}` | `avail.canJoin()` | `true`/`false` |
| `{job_favorite}` | `JobFavoriteService` | `true`/`false` |
| `{job_icon}` | Job definition | `minecraft:diamond_pickaxe` |

Color mapping by status:
- **ACTIVE**: `<green>` (green name, glint enabled)
- **AVAILABLE**: `<dark_green>`
- **LOCKED / PERMISSION_REQUIRED / RANK_REQUIRED / NO_AVAILABLE_SLOT**: `<red>` (no glint)
- **LICENSE_REQUIRED**: `<yellow>`
- **COOLDOWN**: `<gray>`
- **INTEGRATION_UNAVAILABLE / CONFIGURATION_ERROR / ADMIN_DISABLED**: `<dark_gray>`

### Menu YAML Structure

The `jobs_menu` YAML template lives in `config/bigbangessentials/menus/jobs_menu.yml`. It uses the menu system's standard slot/action/condition DSL with jobs-specific placeholders.

Example slot definition:

```yaml
slots:
  job_{job_id}:
    slot: 10-26
    item:
      material: "{job_icon}"
      name: "{job_status_color}{job_display_name}"
      lore:
        - "Nível: {job_level}/{job_max_level}"
        - "{job_xp_progress_bar}"
        - ""
        - "{job_license_label}"
      enchanted: "{#if job_active}true{/if}"
    actions:
      left:
        type: RUN_COMMAND
        value: "/jobs info {job_id}"
```

## Admin Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/jobsadmin debug <player>` | `bigbangessentials.jobs.admin.debug` | Show availability breakdown for target |
| `/jobsadmin reload` | `bigbangessentials.jobs.admin.reload` | Reload jobs config and menus |
| `/jobsadmin forcejoin <player> <job>` | `bigbangessentials.jobs.admin.forcejoin` | Bypass availability checks |
| `/jobsadmin forceleave <player> <job>` | `bigbangessentials.jobs.admin.forceleave` | Force remove from job |
| `/jobsadmin addxp <player> <job> <amount>` | `bigbangessentials.jobs.admin.addxp` | Grant XP bypassing anti-exploit |
| `/jobsadmin setlevel <player> <job> <level>` | `bigbangessentials.jobs.admin.setlevel` | Set job level directly |
| `/jobsadmin reset <player> [job]` | `bigbangessentials.jobs.admin.reset` | Reset player progress |
| `/jobsadmin health` | `bigbangessentials.jobs.admin.debug` | Show integration health statuses |
| `/jobsadmin audit <player>` | `bigbangessentials.jobs.admin.audit` | View audit log for player |

## Permissions

| Node | Default | Purpose |
|------|---------|---------|
| `jobs.command.jobs` | true | Access `/jobs` |
| `jobs.command.list` | true | List jobs |
| `jobs.command.entrar` | true | Join jobs |
| `jobs.command.sair` | true | Leave jobs |
| `jobs.command.info` | true | View job details |
| `jobs.command.ganhos` | true | View earnings |
| `jobs.command.habilidades` | true | View/unlock skills |
| `jobs.command.top` | true | View rankings |
| `jobs.command.license` | true | License management |
| `jobs.command.slot` | true | Slot management |
| `jobs.command.fragmentos` | true | Fragment commands |
| `jobs.command.contratos` | true | Contract commands |
| `bigbangessentials.jobs.admin.*` | op | All admin commands |
| `bigbangessentials.jobs.profession.<id>` | true | Access specific profession |

Permission-based multipliers (checked via `ExternalPermissionAdapter`):
- `jobs.ganhos.X` — where X is a whole number percentage (e.g. `jobs.ganhos.20` = 1.20x earnings)
- `jobs.limite.X` — where X is max active job slots (e.g. `jobs.limite.3`)

## Migration Notes

### From Legacy Text System
1. The text fallback in `/jobs` (executeSummary) still exists if menu fails to open.
2. Old permission nodes (`jobs.command.entrar`, `jobs.command.sair`) remain unchanged.
3. `JobAvailabilityService.evaluate()` now gates all join attempts pre-command — admins use `forcejoin` to bypass.
4. Visibility config replaces the old `hiddenUntilDiscovered` boolean — migrate to `VISIBLE_WHEN_DISCOVERED` mode.

### YAML Menu Changes
- All new placeholders are prefixed with `job_` — update custom menu YAML files.
- License labels now come from availability state, not hardcoded strings.
- Favorite/unfavorite toggling is handled client-side in the menu actions.
