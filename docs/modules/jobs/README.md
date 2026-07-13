# Jobs Module

Profession system with 17 jobs (11 common + 6 Pokemon specializations) for the BigBangEssentials mod on Cobbleverse (NeoForge + Fabric).

## Architecture

```
Config → Domain → Services → Pipeline → Commands/Menus
```

| Layer | Package | Key Classes |
|-------|---------|-------------|
| Config | `jobs.config` | `JobsConfig`, `JobsConfigLoader`, `JobDefinition`, `XpCurve`, `HowToEarn`, `ActionReward`, `SkillDefinition`, `UnlockRequirements` |
| Domain | `jobs` | `JobAction`, `JobActionType`, `JobActionContext`, `PlayerJobsData`, `JobRewardOutcome` |
| Pipeline | `jobs.pipeline` | `JobActionProcessor`, `JobActionValidator`, `JobRuleEvaluator`, `JobRewardCalculator`, `JobRewardApplier`, `JobActionPublisher`, `JobEligibilityResolver` |
| License | `jobs.license` | `JobLicenseService`, `JobLicenseObjective`, `JobLicenseRepository`, `JobLicenseProgressService`, `InProgressLicense`, `PermanentLicense` |
| Slots | `jobs.slot` | `JobSlotService`, `JobSlotDefinition`, `JobSlotType`, `JobSlotRepository`, `JobSwitchCooldownService` |
| Contracts | `jobs.contracts` | `JobContract`, `JobContractService`, `JobContractGenerator`, `ContractObjective`, `ContractReward` |
| Crates | `jobs.crates` | `CrateRewardGateway`, `DefaultCrateRewardGateway`, `CrateOpenRequest`, `CrateOpenResult`, `CrateRewardRecoveryService`, `SpecialistKeyService` |
| Progression | `jobs.progression` | `JobRankMilestoneService`, `RankMilestoneDefinition`, `JobRankProgressProvider`, `RankProgressionSnapshot` |
| Anti-exploit | `jobs.antiexploit` | `BlockProvenanceService`, `PlayerActionEligibilityService`, `CraftingValidationService`, `SmeltingValidationService`, `CropHarvestValidationService`, `ExplorationDiscoveryService`, `ActionRateLimitService`, `ActionCooldownService`, `RepeatActionGuard`, `ProvenanceType`, `ProvenanceResult`, `UnknownBlockPolicy` |
| Pokemon | `jobs.pokemon` | `PokemonJobActionValidator`, `PokemonJobAuditService`, `SpecialistKeyService` |
| Researcher | `jobs.researcher` | `DexDiscoveryService`, `CaptureCorrelationService` |
| Raids | `jobs.raids` | `RaidDeduplicationService` |
| Rewards | `jobs.rewards` | `JourneyFragmentService`, `JourneyFragmentRepository`, `JobRewardLimitService`, `JobRewardRollRepository`, `JobRewardAuditService`, `JobRewardNotificationService`, `JobKeyDropRule`, `CrateRewardDefinition` |
| Admin | `jobs.admin` | `JobAuditService`, `JobAuditEvent` |
| Database | `jobs.database` | `JobsRepository`, `JobActionReceiptRepository` |
| Events | `jobs.events` | `JobsEvents` (JobJoinEvent, JobLeaveEvent, JobLevelUpEvent, JobExperienceGainEvent, JobRewardPaidEvent, etc.) |

### Player Flow

```
Join → License → Slot → Active → Actions → Rewards → Level Up
  ↓        ↓        ↓       ↓         ↓          ↓          ↓
Entrar    Missão   Slot   Ganhos    Pipeline   Moedas     Habilidades
na        curta    livre  liberados  validado   + XP       + pontos
profissão
```

1. **Join**: Player runs `/jobs entrar <id>` or clicks a profession in the menu
2. **License**: If `license-required: true`, a short mission (e.g., capture 50 Pokemon) must be completed
3. **Slot**: An available slot of the correct category (COMMON_PRIMARY, COMMON_SECONDARY, POKEMON_SPECIALIZATION) must be unlocked via RankUp milestones
4. **Active**: Once licensed and slotted, the profession is active and earns money/XP
5. **Actions**: Every valid game action (break block, catch Pokemon, win battle) enters the pipeline
6. **Rewards**: After anti-exploit validation, rule evaluation, and reward calculation, money + XP are applied
7. **Level Up**: Accumulate XP, gain levels, unlock skills, receive journey fragments and crate key rolls

### Components

**Managers**: `JobsManager` (singleton) — central orchestration point. Owns config, repository, player data cache, ranking cache. Handles reload, daily cycle reset.

**Services**: 20+ focused services, each with a single responsibility:
- `JobCommandService` — player commands
- `JobProgressService` — XP and level management
- `JobLevelService` — level-up calculation
- `JobSkillService` — skill trees
- `JobRankingService` — leaderboards
- `JobDailyLimitService` — daily earnings cap
- `JobMessageService` — configurable messages
- `JobLicenseService` — license quest lifecycle
- `JobLicenseProgressService` — license objective tracking
- `JobSlotService` — slot management
- `JobSwitchCooldownService` — switch cooldowns
- `JobContractService` — daily/weekly contracts
- `JobContractGenerator` — dynamic contract creation
- `JobRankMilestoneService` — milestone detection
- `SpecialistKeyService` — Pokemon specialist keys
- `JourneyFragmentService` — fragment economy
- `PokemonJobAuditService` — anti-exploit audit logs
- `JobConfigurationValidator` — config validation
- `JobAntiExploitService` — anti-exploit checks
- `DefaultJobAntiExploitService` — default implementation

**Integrations**: 6 Cobbleverse bridges via `OptionalJobsIntegration` interface, each with a full health state machine.

**Anti-exploit**: Multi-layer validation:
1. Player-placed block detection (`BlockProvenanceService`)
2. AFK detection
3. Admin-spawned entity rejection
4. Trade/passive farm blocking
5. Cooldown and rate limiting
6. Idempotent action processing (`JobActionReceiptRepository`)

**Persistence**: SQLite/MySQL via `JobsRepository` (player job data), `JobLicenseRepository` (licenses), `JobSlotRepository` (slot assignments), `JobActionReceiptRepository` (action dedup), `JobRankMilestoneRepository` (milestones), `JobLicenseProgressRepository` (license progress).

### Initialization Order

```
JobsManager (singleton init)
  → JobsConfigLoader.loadAndValidate()
    → migrateIfNeeded() (config/ → world/serverconfig/bigbangessentials/jobs/)
    → load global.json, slots.json, milestones.json
    → load professions/*.json
    → validate all
  → Integrations (Cobbleverse bridges initialize)
  → Event Listeners (NeoForge event bus registration)
  → JobLicenseProgressService init
```

### Persistence

| Repository | Table | Purpose |
|-----------|-------|---------|
| `JobsRepository` | `bbe_player_jobs` | Player job levels, XP, skill points, active status |
| `JobLicenseRepository` | `bbe_job_licenses` | Permanent license grants |
| `JobLicenseProgressRepository` | `bbe_job_license_progress` | License objective progress |
| `JobSlotRepository` | `bbe_player_job_slots` | Active slot assignments |
| `JobActionReceiptRepository` | `bbe_job_action_receipts` | Action ID deduplication |
| `JobRankMilestoneRepository` | `bbe_rank_milestones` | Player milestone unlocks |
| `JobContractRepository` | `bbe_job_contracts` | Active contracts |

Database fallback: if database is unavailable, methods return empty result sets. Data repopulates on reconnect.

### Events

All events extend `JobsEvents.JobEvent` and are fired on the NeoForge event bus:

| Event | Cancellable | Fired When |
|-------|-------------|------------|
| `JobJoinEvent` | Yes | Player joins a profession |
| `JobLeaveEvent` | Yes | Player leaves a profession |
| `JobExperienceGainEvent` | Yes | XP is about to be awarded (amount modifiable) |
| `JobLevelUpEvent` | No | Player levels up in a profession |
| `JobRewardCalculateEvent` | Yes | Reward is being calculated (multipliers modifiable) |
| `JobRewardPaidEvent` | No | Money has been deposited |
| `JobDailyLimitReachedEvent` | No | Daily earnings limit hit |
| `JobSkillUnlockEvent` | Yes | A skill is about to be unlocked |

### Related Documentation

- [CONFIGURATION.md](CONFIGURATION.md) — Complete config reference
- [PROFESSIONS.md](PROFESSIONS.md) — All 17 profession details
- [COBBLEVERSE_INTEGRATIONS.md](COBBLEVERSE_INTEGRATIONS.md) — Integration architecture
- [PERMISSIONS.md](PERMISSIONS.md) — Permission matrix
- [ADMIN_OPERATIONS.md](ADMIN_OPERATIONS.md) — Admin commands
- [MIGRATION.md](MIGRATION.md) — Migration guide
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) — Common issues
- [Wiki/JobsSystem.md](../../Wiki/JobsSystem.md) — User-facing wiki
- [docs/engineering/jobs/cobbleverse-integrations.md](../../engineering/jobs/cobbleverse-integrations.md) — Integration spec
