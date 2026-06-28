# Gems Troubleshooting and Administrative Controls

This page guides administrators and developers through debugging, auditing, and repairing the Gems system in production.

## Administrative Commands

All administrative commands require specific permissions and use a forced confirmation structure:

### 1. Verify State Integrity
Validates that in-memory balances match calculated reservations and scans the state file schema:
```bash
/gems admin verify
```
- **Permission:** `bigbangessentials.gems.admin.verify`

### 2. Force Repair State File
Recalculates all held balances, checks all reservations, transitions expired entries, and writes a clean state to disk.
```bash
/gems admin repair confirm
```
- **Permission:** `bigbangessentials.gems.admin.repair`
- *Note:* The literal `"confirm"` suffix is strictly required by the Brigadier command node.

### 3. Force Release Reservation
Manually cancel a stuck reservation and release the locked funds back to the player's balance:
```bash
/gems admin reservation release <reservationId> confirm
```
- **Permission:** `bigbangessentials.gems.admin.release`
- *Note:* The literal `"confirm"` suffix is strictly required.

> ⚠️ **CRITICAL SAFETY WARNING**
>
> Never execute:
> ```
> /gems admin reservation release <reservationId> confirm
> ```
> without first confirming that the external consumer (e.g., BigBang Regions) does **NOT** have an operation in one of these states:
>
> - `PAYMENT_RESERVED`
> - `RESIZE_APPLIED`
> - `PAYMENT_CAPTURE_PENDING`
>
> If a release is forced while the consumer has an active operation in one of these states, the consumer may later attempt a `capture()` on a now-RELEASED reservation, causing a `RESERVATION_NOT_ACTIVE` failure. The player's Gems would already be returned to their available balance, but the consumer's workflow would be in an inconsistent state. Always verify the consumer's operation state before using force release.

---

## Recovery and Backup Scenarios

The Gems system automatically saves backup files under `bigbangessentials/gems_backups/` during:
- Automatic recoveries on boot.
- Manual reloads.
- Execution of the repair command.

### Manual Backup Restore
If the main `gems_state.json` file becomes corrupted (e.g. invalid JSON content):
1. Stop the Minecraft server.
2. Navigate to `bigbangessentials/gems_backups/`.
3. Locate the latest valid backup file (e.g. `gems_state_manual_pre_reload_20260627_230252.json`).
4. Copy and rename this file to `bigbangessentials/gems_state.json`.
5. Start the server. The recovery protocol will process the file, expire any old leases, and rebuild consistency.
