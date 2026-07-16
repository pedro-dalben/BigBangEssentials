# Jobs Placeholder Reference

This document lists the placeholders exposed by the Jobs menu integration.

## Where They Work

- `jobs_menu.yml`
- `job_details_menu.yml`
- `pokemon_jobs_menu.yml`
- `pokemon_job_details_menu.yml`
- Any custom menu that uses the same `JobsMenuSupport` integration

`job_*` placeholders are injected per job card or detail view. Detail menus usually reference them through the menu context, for example `{context:job_display_name}`.

`jobs:*` placeholders are resolved by the summary placeholder resolver and are typically used in summary widgets such as the main `jobs_menu.yml` header.

## 1. Job Identity and Presentation

| Placeholder | Value | Notes |
|-------------|-------|-------|
| `job_id` | Job identifier | Canonical profession ID. |
| `job_name` | Job identifier | Legacy alias of `job_id`. |
| `job_display_name` | Display name | Friendly name shown in menus. |
| `job_description` | Full description | Empty string when the job has no description. |
| `job_short_description` | Short description | Compact summary for tooltips or cards. |
| `job_icon` | Item ID | Falls back to `minecraft:book` when not configured. |
| `job_category` | Category key | Raw category value, such as `COMMON` or `POKEMON_SPECIALIZATION`. |
| `job_category_label` | Category label | Human-friendly label such as `Profissão Comum` or `Especialização Pokémon`. |
| `job_max_level` | Max level | Maximum profession level. |

## 2. Progress, Economy, and Display Blocks

| Placeholder | Value | Notes |
|-------------|-------|-------|
| `job_level` | Player job level | Current level in the profession. |
| `job_xp` | Current XP | Formatted with one decimal place. |
| `job_xp_required` | XP required for next level | Formatted with one decimal place. |
| `job_xp_progress_bar` | Progress bar string | Ready-to-render bar for lore lines. |
| `job_earnings` | Earned today | Money already earned today in this job. |
| `job_limit` | Daily cap | Per-job daily limit. |
| `job_earn_money_header` | Money section header | Comes from `how-to-earn.moneyHeader`. |
| `job_earn_xp_header` | XP section header | Comes from `how-to-earn.xpHeader`. |
| `job_earn_money_lines` | Money help text block | Newline-separated block of all money lines. |
| `job_earn_money_line_1`, `job_earn_money_line_2`, ... | Money help text lines | One placeholder per configured money line, starting at 1. |
| `job_earn_xp_lines` | XP help text block | Newline-separated block of all XP lines. |
| `job_earn_xp_line_1`, `job_earn_xp_line_2`, ... | XP help text lines | One placeholder per configured XP line, starting at 1. |

## 3. Availability and Access

| Placeholder | Value | Notes |
|-------------|-------|-------|
| `job_status` | Rendered status text | Human-readable status label from the menu view model. |
| `job_status_color` | Color prefix | Availability-driven color prefix for the title or lore. |
| `job_status_key` | Status key | Lower-case availability key used by the menu system. |
| `job_availability_status` | Availability enum | Raw `JobAvailabilityStatus` name. |
| `job_primary_reason` | Primary reason | Main availability reason returned by the resolver. |
| `job_license_label` | Short license/access label | Short summary used in the default menus, for example `Ativo` or `Bloqueado`. |
| `job_slot_assigned` | Assigned slot | Slot type assigned to the job, or `Nenhum`. |
| `job_active` | Active flag | `true` or `false`, as a string. |
| `job_can_join` | Join flag | `true` or `false`, as a string. |
| `job_can_leave` | Leave flag | `true` or `false`, as a string. |
| `job_favorite` | Favorite flag | `true` or `false`, as a string. |
| `job_required_rank_label` | Required rank label | Friendly RankUp label, or `Não necessário` when no rank is required. |
| `job_required_rank_id` | Required rank ID | Raw required rank id, or an empty string when not configured. |
| `job_license_required_label` | License requirement label | `Obrigatória` or `Não necessária`. |
| `job_license_required` | License requirement flag | `true` or `false`, as a string. |
| `job_required_integration` | Required integration id | Only present when the profession requires another integration, such as `cobblemon`. |

## 4. License State

| Placeholder | Value | Notes |
|-------------|-------|-------|
| `job_license_status` | License status label | Rendered license label, such as `Licença permanente`, `Em andamento`, or `Disponível para iniciar`. |
| `job_license_status_key` | License status key | Lower-case license enum name, such as `licensed`, `in_progress`, or `ready_to_claim`. |
| `job_license_objectives` | Objectives block | Multi-line lore block with the license objectives list. |
| `job_license_progress` | Progress block | Multi-line lore block with the current progress of each objective. |
| `job_license_objectives_count` | Objective count | Total number of configured objectives. |
| `job_license_objectives_completed` | Completed objective count | Number of objectives already completed. |

The license blocks are already formatted for lore usage. If you insert them directly into a menu item, they render as ready-made multiline text.

## 5. Summary Placeholders

These placeholders are resolved by the `jobs` placeholder resolver and are used in the summary widget inside `jobs_menu.yml`.

| Placeholder(s) | Value | Notes |
|----------------|-------|-------|
| `jobs_active_count` / `active_count` | Active jobs count | Number of currently active jobs for the player. |
| `jobs_max_active` / `max_active` | Max active jobs | Maximum number of active jobs allowed for the player. |
| `jobs_total_earnings` / `total_earnings` | Total earnings | Total daily earnings already collected by the player. |
| `jobs_global_limit` / `global_limit` | Global limit | Global daily earnings cap after permission multipliers. |
| `jobs_vip_bonus` / `vip_bonus` | VIP bonus | Permission-based earnings bonus shown as a percentage. |

The resolver accepts both the raw alias and the `jobs_`-prefixed form. For example, `{jobs:active_count}` and `{jobs:jobs_active_count}` both resolve to the same value.

## 6. Menu Examples

### Main menu

```yaml
lore:
  - "<gray>Nível: <white>{job_level}"
  - "<gray>XP: <white>{job_xp} / {job_xp_required}"
  - "{job_xp_progress_bar}"
  - "<gray>Licença: {job_license_required_label}"
  - "<gray>Status da licença: {job_license_status}"
  - "{job_license_objectives}"
  - "{job_license_progress}"
```

### Detail menu

```yaml
lore:
  - "<gray>Rank: <white>{context:job_required_rank_label}"
  - "<gray>Status: {context:job_status_color}{context:job_status}"
  - "<gray>Licença: {context:job_license_required_label}"
  - "{context:job_license_objectives}"
  - "{context:job_license_progress}"
```

### Summary widget

```yaml
lore:
  - "<gray>Trabalhos Ativos: <white>{jobs:active_count} / {jobs:max_active}"
  - "<gray>Limite de Ganhos Hoje: <white>${jobs:total_earnings} / ${jobs:global_limit}"
  - "<gray>Bônus VIP: <green>{jobs:vip_bonus}"
```

## See Also

- [README.md](README.md) - Module overview
- [JobsMenuSystem.md](../../Wiki/JobsMenuSystem.md) - Menu system reference
