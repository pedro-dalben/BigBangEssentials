# Jobs Permission Matrix

## Canonical Prefix

```
bigbangessentials.jobs
```

Configured in `global.json` → `permissions.prefix`. Default: `bigbangessentials.jobs`.

## Legacy Aliases

The system checks both canonical (`bigbangessentials.jobs.*`) and legacy (`jobs.*`) nodes. Legacy aliases are defined in `global.json` → `permissions.legacy-aliases`. If a legacy node is defined, both nodes are checked when authorizing.

## Player Permissions

| # | Node | Legacy Alias | Description | Default | Category | Controls |
|---|------|-------------|-------------|---------|----------|----------|
| 1 | `bigbangessentials.jobs.command.menu` | `jobs.command.jobs` | Open the main jobs menu | Everyone | player | `/jobs` and `/jobs menu` |
| 2 | `bigbangessentials.jobs.command.list` | `jobs.command.list` | List all available professions | Everyone | player | `/jobs list` |
| 3 | `bigbangessentials.jobs.command.join` | `jobs.command.entrar` | Join a profession | Everyone | player | `/jobs entrar <id>` |
| 4 | `bigbangessentials.jobs.command.leave` | `jobs.command.sair` | Leave a profession | Everyone | player | `/jobs sair <id>` |
| 5 | `bigbangessentials.jobs.command.info` | `jobs.command.info` | View profession stats | Everyone | player | `/jobs info <id>` |
| 6 | `bigbangessentials.jobs.command.earnings` | `jobs.command.ganhos` | View daily earnings | Everyone | player | `/jobs ganhos` |
| 7 | `bigbangessentials.jobs.command.skills` | `jobs.command.habilidades` | View skill tree | Everyone | player | `/jobs habilidades` |
| 8 | `bigbangessentials.jobs.command.top` | `jobs.command.top` | View leaderboard | Everyone | player | `/jobs top <id>` |
| 9 | `bigbangessentials.jobs.command.license` | `jobs.command.license` | View license progress | Everyone | player | `/jobs licenca <id>` |
| 10 | `bigbangessentials.jobs.command.slot` | `jobs.command.slot` | Manage active slots | Everyone | player | `/jobs slot` |
| 11 | `bigbangessentials.jobs.command.contracts` | — | Open contracts menu | Everyone | player | `/jobs contratos` |

## Profession Access

| # | Node | Legacy Alias | Description | Default | Category | Controls |
|---|------|-------------|-------------|---------|----------|----------|
| 12 | `bigbangessentials.jobs.profession.<id>` | `jobs.profissao.<id>` | Access to specific profession | Everyone | player | Can join `/<id>` profession |

**Profession IDs**: miner, woodcutter, farmer, builder, blacksmith, crafter, explorer, ranger, culinarian, magician, fisherman, researcher, breeder, trainer, pasture_keeper, paleontologist, raider

## Bonus Multipliers (VIP/Donor)

| # | Node | Legacy Alias | Description | Default | Category | Controls |
|---|------|-------------|-------------|---------|----------|----------|
| 13 | `bigbangessentials.jobs.bonus.earnings` | `jobs.ganhos.*` | Bonus money multiplier | VIP | vip | Extra money per action |
| 14 | `bigbangessentials.jobs.bonus.xp` | `jobs.xp.*` | Bonus XP multiplier | VIP | vip | Extra XP per action |
| 15 | `bigbangessentials.jobs.bonus.dailylimit` | `jobs.limitediario.*` | Increased daily earnings cap | VIP | vip | Higher daily money limit |
| 16 | `bigbangessentials.jobs.bonus.slots` | `jobs.limite.*` | Extra job slots | VIP | vip | More simultaneously active jobs |

## Admin Permissions

| # | Node | Legacy Alias | Description | Default | Category | Controls |
|---|------|-------------|-------------|---------|----------|----------|
| 17 | `bigbangessentials.jobs.admin` | `jobs.admin.*` | Full admin access | OP | admin | All admin commands |
| 18 | `bigbangessentials.jobs.admin.reload` | — | Reload configuration | OP | admin | `/jobsadmin reload` |
| 19 | `bigbangessentials.jobs.admin.info` | — | View system info | OP | admin | `/jobsadmin info` |
| 20 | `bigbangessentials.jobs.admin.join` | — | Force-join profession | OP | admin | `/jobsadmin entrar` |
| 21 | `bigbangessentials.jobs.admin.leave` | — | Force-leave profession | OP | admin | `/jobsadmin sair` |
| 22 | `bigbangessentials.jobs.admin.setlevel` | — | Set player level | OP | admin | `/jobsadmin setlevel` |
| 23 | `bigbangessentials.jobs.admin.xp` | — | Add/remove XP | OP | admin | `/jobsadmin addxp / removexp` |
| 24 | `bigbangessentials.jobs.admin.reset` | — | Reset player data | OP | admin | `/jobsadmin reset` |
| 25 | `bigbangessentials.jobs.admin.resetearnings` | — | Reset daily earnings | OP | admin | `/jobsadmin resetganhos` |
| 26 | `bigbangessentials.jobs.admin.skillpoints` | — | Manage skill points | OP | admin | `/jobsadmin pontos` |
| 27 | `bigbangessentials.jobs.admin.unlock` | — | Unlock profession | OP | admin | `/jobsadmin desbloquear` |
| 28 | `bigbangessentials.jobs.admin.lock` | — | Lock profession | OP | admin | `/jobsadmin bloquear` |
| 29 | `bigbangessentials.jobs.admin.debug` | — | Toggle debug mode | OP | admin | `/jobsadmin debug` |
| 30 | `bigbangessentials.jobs.admin.diag` | — | Run diagnostics | OP | admin | `/jobsadmin diag` |
| 31 | `bigbangessentials.jobs.admin.integrations` | — | View/Probe integrations | OP | admin | `/jobsadmin integrations` |
| 32 | `bigbangessentials.jobs.admin.audit` | — | View audit logs | OP | admin | `/jobsadmin audit` |
| 33 | `bigbangessentials.jobs.admin.pokemon` | — | Pokemon admin commands | OP | admin | `/jobsadmin pokemon` |
| 34 | `bigbangessentials.jobs.admin.license` | — | Manage licenses | OP | admin | `/jobsadmin licenca` |
| 35 | `bigbangessentials.jobs.admin.slot` | — | Manage slots | OP | admin | `/jobsadmin slot` |
| 36 | `bigbangessentials.jobs.admin.migrate` | — | Run data migration | Console | admin | `/jobsadmin migrate` |

## Moderator Permissions (Subset)

For moderators who need limited admin access:

| # | Node | Description | Category |
|---|------|-------------|----------|
| 37 | `bigbangessentials.jobs.moderator` | Base moderator access | moderator |
| 38 | `bigbangessentials.jobs.moderator.info` | View system info | moderator |
| 39 | `bigbangessentials.jobs.moderator.audit` | View audit logs | moderator |
| 40 | `bigbangessentials.jobs.moderator.pokemon` | View pokemon status | moderator |

## LuckyPerms Setup Examples

### Player (Default)
```
lp group default permission set bigbangessentials.jobs.command.menu true
lp group default permission set bigbangessentials.jobs.command.list true
lp group default permission set bigbangessentials.jobs.command.join true
lp group default permission set bigbangessentials.jobs.command.leave true
lp group default permission set bigbangessentials.jobs.command.info true
lp group default permission set bigbangessentials.jobs.command.earnings true
lp group default permission set bigbangessentials.jobs.command.skills true
lp group default permission set bigbangessentials.jobs.command.top true
lp group default permission set bigbangessentials.jobs.command.license true
lp group default permission set bigbangessentials.jobs.command.slot true
lp group default permission set bigbangessentials.jobs.command.contracts true
lp group default permission set bigbangessentials.jobs.profession.miner true
lp group default permission set bigbangessentials.jobs.profession.woodcutter true
lp group default permission set bigbangessentials.jobs.profession.farmer true
lp group default permission set bigbangessentials.jobs.profession.builder true
lp group default permission set bigbangessentials.jobs.profession.blacksmith true
lp group default permission set bigbangessentials.jobs.profession.crafter true
lp group default permission set bigbangessentials.jobs.profession.explorer true
lp group default permission set bigbangessentials.jobs.profession.ranger true
lp group default permission set bigbangessentials.jobs.profession.culinarian true
lp group default permission set bigbangessentials.jobs.profession.magician true
lp group default permission set bigbangessentials.jobs.profession.fisherman true
```

### VIP / Donor
```
lp group vip permission set bigbangessentials.jobs.bonus.earnings true
lp group vip permission set bigbangessentials.jobs.bonus.xp true
lp group vip permission set bigbangessentials.jobs.bonus.dailylimit true
lp group vip permission set bigbangessentials.jobs.bonus.slots true
```

### Pokemon Spec Access (Typically Adept rank and above)
```
lp group adept permission set bigbangessentials.jobs.profession.researcher true
lp group adept permission set bigbangessentials.jobs.profession.breeder true
lp group adept permission set bigbangessentials.jobs.profession.trainer true
lp group adept permission set bigbangessentials.jobs.profession.pasture_keeper true
lp group adept permission set bigbangessentials.jobs.profession.paleontologist true
lp group adept permission set bigbangessentials.jobs.profession.raider true
```

### Moderator
```
lp group moderator permission set bigbangessentials.jobs.moderator true
lp group moderator permission set bigbangessentials.jobs.moderator.info true
lp group moderator permission set bigbangessentials.jobs.moderator.audit true
lp group moderator permission set bigbangessentials.jobs.moderator.pokemon true
```

### Admin / Operator
```
lp group admin permission set bigbangessentials.jobs.admin true
```

### Console-Only
```
lp group console permission set bigbangessentials.jobs.admin.migrate true
```

## Permission Resolution Order

1. Check canonical node (`bigbangessentials.jobs.*`)
2. If denied, check legacy alias (`jobs.*`) — if both exist and either grants, permission passes
3. If `visible-without-permission` is `true` in profession config, menu shows the job even without the profession node

## Default Assignments

| Role | Nodes Granted |
|------|---------------|
| Everyone | 1–11 (all player commands), 12 (profession access for COMMON jobs) |
| VIP | 13–16 (bonus multipliers) |
| Adept+ | 12 (POKEMON_SPECIALIZATION jobs) |
| Moderator | 37–40 (read-only admin) |
| Admin/OP | 17–35, 37–40 (full admin) |
| Console | 36 (migration) |
