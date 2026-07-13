# Admin Operations

## Admin Commands

All admin commands use `/jobsadmin` (console-compatible) or `/jobs admin` (in-game alias).

### `/jobsadmin reload`
Reloads all configuration from disk without server restart.

**What happens:**
1. `JobsConfigLoader.loadAndValidate()` is called
2. New `JobsConfig` object is built (professions, slots, milestones, global)
3. Old config atomically swapped if validation passes
4. Integrations: all bridges are shutdown (listeners removed), then re-probed and re-subscribed
5. Player data caches are **not** cleared
6. Daily cycle is recalculated against new timezone/reset-time
7. Config backup is created before swap

**What to check after:**
- Server log for validation errors
- `/jobsadmin integrations probe` — verify all bridges healthy
- `/jobsadmin diag` — verify system state
- Test a player action to confirm rewards still flow

**Rollback**: If validation fails, old config remains active. Logs show the error.

### `/jobsadmin info <player>`
Displays complete player info: active jobs, levels, XP, daily earnings, skill points, slot assignments, license status.

### `/jobsadmin entrar <player> <jobId>`
Force-join a player into a profession. Bypasses license requirement (auto-grants permanent license) and slot checks.

### `/jobsadmin sair <player> <jobId>`
Force-leave a player from a profession. Respects `reset-progress-on-leave` setting.

### `/jobsadmin setlevel <player> <jobId> <level>`
Set a player's profession level directly. Triggers level-up events and skill point grants for intermediate levels.

### `/jobsadmin addxp <player> <jobId> <amount>`
Add XP to a player's profession. Triggers level-up if threshold reached.

### `/jobsadmin removexp <player> <jobId> <amount>`
Remove XP. Cannot go below 0 for current level.

### `/jobsadmin reset <player> <jobId>`
Reset a player's profession data (level to 1, XP to 0, skills cleared, daily earnings zeroed). Does not remove license or slots.

### `/jobsadmin resetganhos <player>`
Reset daily earnings for a player (all professions). Useful after adjusting daily limits.

### `/jobsadmin pontos <player> <jobId> <points>`
Set skill points for a player's profession.

### `/jobsadmin desbloquear <player> <jobId>`
Unlock a profession (grant permanent license + slot assignment).

### `/jobsadmin bloquear <player> <jobId>`
Lock a profession (revoke license, remove from slot, stop earnings).

### `/jobsadmin debug <player>`
Toggle debug mode on/off for a player. Debug mode shows detailed reward calculation info in chat.

### `/jobsadmin diag`
Run system diagnostics:
- Config load status
- Number of professions loaded
- Number of slots defined
- Number of milestones
- Integration health summary (all 6 bridges)
- Database connection status
- Active player count
- Permission prefix configured
- Legacy aliases count

### `/jobsadmin audit <player>`
Display recent anti-exploit audit events for a player: rejected actions with timestamps and reasons.

### `/jobsadmin pokemon status <player>`
Display Pokemon-specific timers and state:
- Capture cooldowns
- Species spam timer (3s)
- Trainer battle cooldowns (per tier)
- Last capture species
- Last battle trainer ID
- Raid dedup entries

### `/jobsadmin pokemon grantkey <player> <amount>`
Grant specialist crate keys to a player.

### `/jobsadmin pokemon resetcd <player>`
Reset all Pokemon-related cooldowns for a player (capture, battle, species spam).

### `/jobsadmin licenca grant <player> <jobId>`
Grant a permanent license to a player, bypassing license quest.

### `/jobsadmin licenca revoke <player> <jobId>`
Revoke a permanent license. Player must re-complete license quest.

### `/jobsadmin licenca reset <player> <jobId>`
Reset license progress (set back to 0 on all objectives).

### `/jobsadmin slot assign <player> <jobId> <slotType>`
Manually assign a job to a slot.

### `/jobsadmin slot clear <player> <slotType>`
Clear a slot assignment.

---

## Integration Diagnostics

### `/jobsadmin integrations`

Display real-time health status of all 6 Cobbleverse bridges. Color-coded output:

| Color | Meaning |
|-------|---------|
| Green | ACTIVE — bridge subscribed and receiving events |
| Yellow | API_FOUND — event class found but not yet subscribed |
| Orange | DEGRADED — bridge active but recent handler error |
| Red | ERROR — fatal integration failure |
| Gray | MOD_NOT_INSTALLED — required mod absent |
| Dark Gray | SHUTDOWN — bridge intentionally stopped |

Per-bridge data shown:
- Integration ID
- Current state
- Detected mod ID and version
- Adapter type (REFLECTIVE / NONE)
- Event class subscribed to
- Event bus used
- Subscription status (SUBSCRIBED / FAILED / NOT_SUBSCRIBED)
- Total events received / accepted / rejected
- Timestamp of last event and last success
- Last error message (if any)

### `/jobsadmin integrations probe`

Execute safe re-probe of all integrations without duplicating listeners. Each bridge:
1. Unsubscribes existing listeners (if any)
2. Re-probes mod presence and event classes
3. Re-subscribes if event classes found
4. Reports new state

---

## Reload Operation Detail

### What Happens
1. `createBackup()` — copies current config dir to `world/serverconfig/bigbangessentials/jobs_backup_<timestamp>/`
2. `loadAndValidate()` — reads all JSON files, builds config objects, runs validation
3. Integration shutdown — all bridge `shutdown()` methods called
4. Atomic swap — `this.config = newConfig`
5. Integration re-init — all bridges re-probe and re-subscribe

### What to Check After Reload
1. **Logs**: No ERROR or WARN entries from `JobsConfigLoader` or bridge classes
2. **Integrations**: `/jobsadmin integrations probe` — all expected bridges ACTIVE
3. **Config**: `/jobsadmin diag` — correct counts for professions (17), slots (3), milestones (3)
4. **Live test**: Have a player perform an action in an active profession, verify reward

### Reload Failure
If reload returns `false`:
1. Old config remains active
2. Check logs for the specific validation error
3. Fix the config file
4. Retry reload

---

## Validation

### `/jobsadmin diag`

Runs a comprehensive diagnostic that checks:
- JobsConfig is non-null
- Global config schema version
- Profession count (should be 17)
- Slot count (should be 3)
- Milestone count
- Database connection (`DatabaseManager.isReady()`)
- Player cache size
- Daily limit configuration
- AFK prevention settings
- Permission prefix
- Integration bridge status summary

All output goes to server console and admin chat.

---

## Backup

### Automatic Backup

**On migration**: When `migrateIfNeeded()` detects legacy configs AND canonical configs exist, they are copied to:
```
world/serverconfig/bigbangessentials/jobs_backup_<yyyyMMdd_HHmmss>/
```

**On reload**: `createBackup()` is called before the config swap:
```
world/serverconfig/bigbangessentials/jobs_backup_<yyyyMMdd_HHmmss>/
```

**On first write**: If defaults are generated, no backup is created (no existing configs to protect).

### Manual Backup
```bash
cp -r world/serverconfig/bigbangessentials/jobs/ world/serverconfig/bigbangessentials/jobs_backup_manual/
```

### Restore from Backup
1. Stop the server
2. Replace `world/serverconfig/bigbangessentials/jobs/` with backup contents
3. Start the server

---

## Emergency Procedures

### Config Corruption (Server Won't Start)

1. **Check logs** for the specific file and error:
   ```
   [ERROR] Failed to load profession config professions/researcher.json: Invalid JSON...
   ```
2. **Fix or replace** the corrupted file:
   - Delete the file — defaults will regenerate on next start
   - Restore from backup
   - Edit the JSON (validate with a JSON linter)
3. **Start server** — default files regenerate for any missing profession JSONs

### Config Corruption (Server Running — Reload Failed)

1. Reload failed, old config still active. Server continues running.
2. **Fix the config file** identified in the error log
3. Retry `/jobsadmin reload`
4. If still failing, restore from a known-good backup and reload

### Database Failure

**Symptoms**:
- Player data not loading (level shows 1, XP shows 0)
- `JobsRepository.isDatabaseAvailable()` returns false
- Actions not persisting

**What happens**:
- `JobsRepository` returns empty data (`new HashMap<>()`) when DB is unavailable
- Player plays with in-memory data that resets on logout
- Actions may not deduplicate correctly (receipts not stored)

**Recovery**:
1. Check database connection: `/jobsadmin diag`
2. Restart database
3. If using SQLite, check file permissions: `chmod 644 config/bigbangessentials/bbe.db`
4. If using MySQL, verify credentials in database config
5. Server will auto-reconnect and repopulate caches

### Player Data Reset (Accidental)

1. **Stop server immediately** (prevent auto-save)
2. **Restore database**: Replace `bbe.db` (SQLite) or restore MySQL table from backup
3. **Restart server**
4. If no database backup, use admin commands to restore levels:
   ```
   /jobsadmin setlevel <player> <job> <level>
   /jobsadmin pontos <player> <job> <points>
   ```

### Integration Degraded After Mod Update

1. Run `/jobsadmin integrations probe`
2. Check `lastError` for each bridge
3. If event class moved: check Cobblemon changelog, update bridge class references
4. If subscription method changed: reflection may fail, check Cobblemon API docs
5. Reload after mod update: `/jobsadmin reload`

### All Jobs Show as "Test"

**Cause**: Config files missing from `world/serverconfig/bigbangessentials/jobs/professions/`.

**Fix**: Delete the professions directory (or individual files) and reload. Defaults will regenerate:
```bash
rm world/serverconfig/bigbangessentials/jobs/professions/*.json
```
Then `/jobsadmin reload` — all 17 defaults are created.
