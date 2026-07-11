# Jobs Troubleshooting Guide

## Common Issues

### "Jobs appear as Test"

**Symptom**: Profession names show as "Test" in menus and chat.

**Cause**: Config files are missing from `config/bigbangessentials/jobs/professions/`. The system falls back to placeholder display names when no config is loaded.

**Fix**:
1. Check if files exist: `ls config/bigbangessentials/jobs/professions/`
2. If empty or missing files, delete what's there and reload:
   ```bash
   rm config/bigbangessentials/jobs/professions/*.json
   ```
3. Run `/jobsadmin reload` — all 17 defaults regenerate automatically
4. Verify: `/jobsadmin diag` should show 17 professions

---

### "Pokemon professions don't appear"

**Symptom**: Researcher, Breeder, Trainer, etc. not visible in menu.

**Causes and fixes**:

| Cause | Fix |
|-------|-----|
| Rank milestone not reached | Player must reach `adept` rank (or equivalent) to unlock `POKEMON_SPECIALIZATION` slot. Check `/jobsadmin info <player>` |
| Cobblemon not installed | Pokemon jobs require Cobblemon. Check `/jobsadmin integrations` — `cobblemon_base` should be ACTIVE. If MOD_NOT_INSTALLED, install Cobblemon |
| Profession disabled | Check `enabled: false` in the profession JSON. Set to `true` and reload |
| Permission missing | Verify `bigbangessentials.jobs.profession.researcher` (etc.) is granted. Check `visible-without-permission` in config |
| Integration BLOCKED | If mod is present but event class not found, integration may be DEGRADED. Check `/jobsadmin integrations probe` |

---

### "Menu opens without title"

**Symptom**: Jobs menu opens but has no title or garbled title.

**Cause**: Menu YAML file missing or corrupted in `config/bigbangessentials/menus/`.

**Fix**:
1. Check `config/bigbangessentials/menus/jobs_menu.yml` exists
2. Remove it and let it regenerate (if auto-generation is implemented)
3. Or manually create with proper YAML structure
4. Reload: `/jobsadmin reload`

---

### "Job doesn't progress"

**Symptom**: Player performs valid actions but earns no money or XP.

**Diagnosis flow**:

1. **Check active status**: `/jobsadmin info <player>` — is the job `active: true`?
2. **Check slot**: Is the job assigned to a valid slot? `/jobsadmin slot list <player>`
3. **Check daily limit**: Has the player reached their daily earnings cap?
   ```
   /jobsadmin info <player> → check dailyEarnings vs maxDailyEarnings
   ```
4. **Check AFK**: Is `prevent-earnings-while-afk` enabled and is the player AFK?
5. **Check anti-exploit**: Run `/jobsadmin audit <player>` — are actions being rejected? Common reasons:
   - Player-placed block (breaking your own placed ore block)
   - Admin-spawned entity (killing a command-spawned mob)
   - Cooldown active (same species too fast for Pokemon)
   - PvP battle (trainer jobs only count NPC battles)

**Fix**: Address the specific blocking condition. For daily limits, wait until reset or run `/jobsadmin resetganhos <player>`.

---

### "Integration shows active but no rewards"

**Symptom**: `/jobsadmin integrations` shows `ACTIVE` but event counters are 0.

**Cause**: Event subscription succeeded but events are not being received by the handler.

**Diagnosis**:
1. `/jobsadmin integrations probe` — check `lastEvent` timestamp and `eventsReceived` counter
2. Check `lastError` field — handler may be throwing silently
3. Verify event bus compatibility — Cobblemon may have changed the event class or bus in a newer version

**Fixes**:
- If `lastError` shows a reflection error: Cobblemon version mismatch. Check changelog
- If event bus changed: bridge needs code update (the reflective probe should detect changes, but class name or field name may differ)
- Try a manual server reload: `/jobsadmin reload` forces re-probe and re-subscribe
- As workaround: switch to contract-based earning for that profession

---

### "Permission denied"

**Symptom**: Player can't use a command despite having it in their permissions.

**Diagnosis**:

1. **Check canonical node**: Does player have `bigbangessentials.jobs.command.join`?
2. **Check legacy alias**: Does player have `jobs.command.entrar`?
3. **Check permission prefix**: Run `/jobsadmin diag` → verify `permissionPrefix` is `bigbangessentials.jobs`
4. **Check config override**: Profession JSON may have a custom `permission` field
5. **Check LuckPerms**:
   ```
   lp user <player> permission info
   ```
   Verify the node is present and not negated

**Fixes**:
- Grant missing permission: `lp user <player> permission set bigbangessentials.jobs.command.join true`
- If using legacy nodes, verify they exist in `global.json` → `permissions.legacy-aliases`
- Reload config: `/jobsadmin reload`

---

### "License doesn't progress"

**Symptom**: License objectives show 0/X even after performing the required actions.

**Causes and fixes**:

| Cause | Fix |
|-------|-----|
| Action type mismatch | Check `license-objectives[].action-type` in profession JSON. Must match the `JobActionType` used by the action (e.g., `POKEMON_CAPTURED`, not `POKEMON-CAPTURED`) |
| Target mismatch | If `match-target-ids` or `match-tags` are specified, only actions matching those targets count |
| Anti-exploit rejection | If the action is rejected by anti-exploit checks, it won't count toward license. Check `/jobsadmin audit <player>` |
| Job not joined | Player must have `joined` the job (even if not yet licensed). Run `/jobs entrar <id>` first |
| Non-player-placed requirement | If `require-non-player-placed: true`, breaking player-placed blocks won't count |
| Mature requirement | If `require-mature: true` on HARVEST actions, only fully grown crops count |

---

### "Slot doesn't appear"

**Symptom**: Player wants to join a profession but no slot is available.

**Causes**:

| Cause | Fix |
|-------|-----|
| Rank milestone not reached | Each slot type unlocks at a specific milestone. Check milestones.json: `"adept"` unlocks `POKEMON_SPECIALIZATION`, `"veteran"` unlocks `COMMON_SECONDARY` |
| Max active jobs reached | Player has `max-active-jobs` (default 2) already active. Must leave or switch one |
| Switch cooldown active | Player recently switched jobs. Wait `switch-cooldown-minutes` (default 30) or reset with admin command |
| Slot already occupied | That slot type already has a job assigned. Clear it first: `/jobs sair <jobId>` |

**Check**: `/jobsadmin info <player>` shows current slot assignments and cooldown state.

---

### "Reload fails"

**Symptom**: `/jobsadmin reload` returns error or jobs disappear after reload.

**Diagnosis**:

1. **Check server logs** for validation errors:
   ```
   [ERROR] Failed to load profession config professions/miner.json: ...
   [ERROR] Configuration loader returned null config.
   ```
2. **Identify the broken file** from the error message
3. **Fix the file**: Common issues:
   - Invalid JSON syntax (missing comma, trailing comma, unquoted key)
   - Missing required field (`id`, `display-name`, `category`, `icon`)
   - Negative reward values
   - Unknown category (must be `COMMON` or `POKEMON_SPECIALIZATION`)
   - Duplicate profession ID
   - Circular skill dependency
   - `max-level` < 1
4. **After fixing**: Run `/jobsadmin reload` again

**Rollback**: If reload fails, the old config remains active. Server is not affected.

---

### "Duplicate rewards"

**Symptom**: Player receives money/XP twice for the same action.

**Cause**: Event deduplication failed. The `JobActionReceiptRepository` should prevent this, but may fail if:
- Database is unavailable (receipts not persisted)
- Action ID is non-deterministic (different UUID each time for same action)

**Diagnosis**:
1. Check database: `/jobsadmin diag` → verify database connection
2. Check `JobActionReceiptRepository` table: `bbe_job_action_receipts`
3. Verify the action generating a stable action ID

**Fix**:
- Restore database connection
- If receipts table is corrupted, truncate it: `DELETE FROM bbe_job_action_receipts WHERE created_at < ...` (clean old entries)
- For Pokemon actions, verify the bridge is using deterministic UUIDs (`UUID.nameUUIDFromBytes(...)`)

---

### "Mod not installed" (Pokemon jobs)

**Symptom**: Pokemon profession shows in menu but with a warning or error icon. Cannot earn real-time rewards.

**Cause**: `BLOCKED_BY_ENVIRONMENT`. The required Cobblemon addon mod is not in the modpack. Integration is in `MOD_NOT_INSTALLED`.

**Which jobs are affected**:
- `pasture_keeper` (needs Pasture addon)
- `paleontologist` (needs Fossil addon)
- `raider` (needs Raid Dens addon)

**If Cobblemon itself is missing**: `researcher`, `breeder`, and `trainer` also affected.

**Workaround**: These jobs can earn via contracts. Players accept contracts from `/jobs contratos` that include objectives for those professions.

**Fix**: Install the required mod and reload: `/jobsadmin reload`.

---

### "Database unavailable"

**Symptom**: Player data not loading, levels reset to 1, earnings not tracking.

**Cause**: `DatabaseManager.getInstance().isReady()` returns false.

**What happens internally**: `JobsRepository` methods check `isDatabaseAvailable()` and return empty data on failure:
```java
if (!isDatabaseAvailable()) {
    return CompletableFuture.completedFuture(new HashMap<>());
}
```

**This means**: Player plays with in-memory data that resets on logout. Actions may not deduplicate (receipts stored only in DB).

**Fixes**:
- **SQLite**: Check file permissions
  ```bash
  chmod 644 config/bigbangessentials/bbe.db
  ```
- **MySQL**: Verify connection settings in database config
  ```yaml
  host: localhost
  port: 3306
  database: bigbangessentials
  username: ...
  password: ...
  ```
- **Both**: Check server logs for database errors
- **Restart**: Database connection is established on server startup

**Recovery**: When database reconnects, `JobsRepository` will load and save normally. In-memory data from the outage period is lost.

---

## Quick Reference Card

| Symptom | First Check | Fast Fix |
|---------|------------|----------|
| "Test" names | `ls config/bigbangessentials/jobs/professions/` | Delete files, `/jobsadmin reload` |
| No Pokemon jobs | `/jobsadmin integrations` | Check Cobblemon installed + player rank |
| No title in menu | `ls config/bigbangessentials/menus/` | Restore menu YAML |
| No progress | `/jobsadmin info <player>` | Check active/slot/daily limit/AFK |
| Active but no rewards | `/jobsadmin integrations probe` | Check lastError, reload |
| Permission denied | `lp user <player> permission info` | Grant canonical node |
| License stuck | `/jobsadmin audit <player>` | Check action type match + anti-exploit |
| No slot | `/jobsadmin info <player>` | Check milestone + cooldown |
| Reload fails | Server logs | Fix JSON, retry |
| Duplicate rewards | `/jobsadmin diag` | Check DB availability |
| Mod not installed | `/jobsadmin integrations` | Use contracts or install mod |
| DB unavailable | `/jobsadmin diag` | Fix connection, restart |
