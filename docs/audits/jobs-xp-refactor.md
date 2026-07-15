# Relatorio de Refatoracao: Fluxo de XP e Dinheiro do Modulo de Jobs

## Diagnostico das Causas

### 1. Minerador recompensando qualquer bloco
**Causa:** A configuracao `miner.json` possuia `"*"` (wildcard) em `BREAK-BLOCK`. O `JobRuleEvaluator` usava `getWildcardReward` como fallback prioritario, fazendo grama, terra e qualquer bloco concederem XP.
**Correcao:** Removido o fallback wildcard. O avaliador agora opera como allowlist: apenas registry IDs exatos ou tags explicitamente configuradas concedem recompensa. Adicionado `default-reward` tipado para casos legitimos (apenas EXPLORE).

### 2. Fazendeiro nao funcionando
**Causa:** O `JobsEventListener.onBlockBreak` publicava toda quebra como `BREAK_BLOCK`. O Fazendeiro espera `HARVEST_CROP`. A classificacao semantica nao existia.
**Correcao:** Criado o `JobActionClassifier` e alterado o listener para verificar `CropHarvestValidationService.isCrop()` antes de publicar. Se for crop, publica `HARVEST_CROP`. Senao, publica `BREAK_BLOCK`. Nunca publica ambos para o mesmo bloco.

### 3. Explorador consumindo descobertas antes da validacao
**Causa:** `ExplorationDiscoveryService.checkAndRecordBiome` registrava a descoberta antes de verificar se o jogador possuia o Explorador ativo.
**Correcao:** Fluxo em 3 fases: `reserveDiscovery` (atomico) -> pipeline valida -> `confirmDiscovery` (sucesso) ou `cancelDiscovery` (falha). Jogador sem Explorador ativo cancela a reserva e nao consome a descoberta.

### 4. Idempotencia baseada apenas em UUID aleatorio
**Causa:** `JobActionReceiptRepository` usava apenas `UUID.randomUUID()` como chave de idempotencia, insuficiente para eventos duplicados de plugins ou lag.
**Correcao:** Adicionado `JobFingerprintService` com SHA-256 deterministico baseado em: loader, player UUID, server tick, action type, dimension, position, registry ID. Fingerprints efemeros (5s TTL) e persistentes (30min TTL) complementam o UUID.

### 5. Plantações do jogador rejeitadas
**Causa:** `PlayerActionEligibilityService` rejeitava todo `playerPlacedBlock` para `BREAK_BLOCK` e `HARVEST_CROP`. Cultivos plantados e maduros nao podiam recompensar.
**Correcao:** Separada a politica: `BREAK_BLOCK` sempre rejeita blocos colocados. `HARVEST_CROP` permite cultivos plantados pelo jogador se estiverem completamente maduros.

### 6. CropHarvestValidationService com fallback perigoso
**Causa:** O metodo `isMatureCrop` retornava `true` para qualquer bloco sem propriedade `age`, tratando-o como maduro.
**Correcao:** Retorna `false` para blocos desconhecidos. Adicionados metodos especificos: `isCrop()`, `isHarvestableProduce()`, `isMultiBlockPlant()`, `isValidHarvestTransition()`.

## Arquivos Alterados

### Novos Arquivos (4)
| Arquivo | Descricao |
|---------|-----------|
| `jobs/pipeline/RawJobEvent.java` | Modelo imutavel de evento raw antes da classificacao |
| `jobs/pipeline/JobActionClassifier.java` | Classifica eventos em acoes semanticas (HARVEST_CROP vs BREAK_BLOCK) |
| `jobs/pipeline/JobFingerprintService.java` | Fingerprint deterministico para deduplicacao |
| `test/.../JobsXpRefactorTest.java` | 450+ linhas de testes: miner, farmer, explorer, geral |

### Arquivos Modificados (12)
| Arquivo | Mudancas |
|---------|----------|
| `jobs/antiexploit/CropHarvestValidationService.java` | Novos metodos: isCrop, isHarvestableProduce, isMultiBlockPlant, isValidHarvestTransition. isMatureCrop retorna false para desconhecidos |
| `jobs/antiexploit/ExplorationDiscoveryService.java` | Refatorado: reserveDiscovery/confirmDiscovery/cancelDiscovery. Loading state. NULL-guard no loading |
| `jobs/antiexploit/PlayerActionEligibilityService.java` | HARVEST_CROP nao e mais rejeitado por player-placed |
| `jobs/listeners/JobsEventListener.java` | Classificacao semantica block break vs harvest. Exploration usa reserveDiscovery. Fortuna Natural so executa para BREAK_BLOCK |
| `jobs/pipeline/JobActionProcessor.java` | Fingerprint dedup. cancelDiscoveryIfPending. Usa MatchResult em vez de Optional |
| `jobs/pipeline/JobRuleEvaluator.java` | Remove wildcard. Add default-reward. Deterministic tag priority. Retorna MatchResult |
| `jobs/pipeline/JobActionValidator.java` | Politica diferenciada para BREAK_BLOCK vs HARVEST_CROP |
| `jobs/pipeline/JobRewardApplier.java` | Confirmacao de descoberta apos recompensa bem-sucedida |
| `jobs/config/JobsConfig.java` | Adicionado metodo getDefaultReward |
| `jobs/config/JobsConfigLoader.java` | Validacao de wildcards. Schema version 3. Remocao de wildcards dos defaults. isEconomicActionType helper |
| `jobs/command/JobsAdminCommand.java` | Comandos trace, explain block, explain action |
| `fabric/.../FabricEvents.java` | JobsEventListener.onPlayerLoggedIn adicionado ao JOIN handler |
| `neoforge/.../NeoForgeEvents.java` | Verificacao !isCanceled() em block break, place, death, fish |

### Arquivos de Configuracao (17)
Todas as 17 profissoes tiveram `"*"` removido das acoes economicas:
- miner, woodcutter, farmer, builder, blacksmith, crafter, ranger, culinarian, magician, fisherman: `"*"` removido
- explorer: `"*"` convertido para `"default-reward"`
- researcher, breeder, trainer, pasture_keeper, paleontologist, raider: `"*"` removido

## Comparacao Conceitual com mcMMO

| Conceito | mcMMO | BigBangEssentials |
|----------|-------|-------------------|
| Classificacao de acao | ExperienceAPI.getXp() -> SkillType | JobActionClassifier -> JobActionType |
| Validacao de contexto | BlockUtils, EventUtils checks | JobActionValidator + Eligibility |
| Resolucao de XP | AdvancedConfig -> experience.yml | JobRuleEvaluator (exact -> tag -> default-reward) |
| Aplicacao de XP | beginXpGain() pipeline | JobActionProcessor pipeline |
| Deduplicacao | hashCode-based | UUID + fingerprint SHA-256 |
| Anti-exploit | placed block tracking | BlockProvenanceService + RepeatActionGuard + Cooldown |
| Multiplicadores | permission, skill, party | permission, skill, level bonus, temporary boosts |

## Arquitetura Final

```
Platform Event (NeoForge/Fabric)
    |
    v
JobsEventListener (classify: crop vs break)
    |
    v
JobAction (UUID + semantic type + context)
    |
    v
JobActionPublisher -> JobActionProcessor
    |
    +-- 1. Idempotency (UUID + fingerprint)
    +-- 2. Validation (anti-exploit, maturity, eligibility)
    +-- 3. Eligibility Resolution (active jobs)
    +-- 4. Rule Evaluation (allowlist: exact -> tag -> default-reward)
    +-- 5. Reward Calculation (base + multipliers + limits)
    +-- 6. Reward Application (money + XP + side effects)
    +-- 7. Receipt (memory + DB)
    +-- 8. Discovery confirmation/cancellation
    +-- 9. Integrations (RankUp, Contracts, Crates)
```

## Matriz das 17 Profissoes

| Profissao | Acoes | Eventos | Loader | Status |
|-----------|-------|---------|--------|--------|
| miner | BREAK_BLOCK | BlockBreak | NeoForge+Fabric | ACTIVE |
| woodcutter | BREAK_BLOCK | BlockBreak | NeoForge+Fabric | ACTIVE |
| farmer | HARVEST_CROP, KILL_ENTITY | BlockBreak+LivingDeath | NeoForge+Fabric | ACTIVE |
| builder | PLACE_BLOCK | BlockPlace | NeoForge+Fabric | ACTIVE |
| blacksmith | SMELT_ITEM | ItemSmelted | NeoForge+Fabric | ACTIVE |
| crafter | CRAFT_ITEM | ItemCrafted | NeoForge+Fabric | ACTIVE |
| explorer | EXPLORE | PlayerTick(biome/cell/structure) | NeoForge+Fabric | ACTIVE |
| ranger | KILL_ENTITY | LivingDeath | NeoForge+Fabric | ACTIVE |
| culinarian | CRAFT_ITEM | ItemCrafted | NeoForge+Fabric | ACTIVE |
| magician | USE_MAGIC | Enchant+Brew | NeoForge+Fabric | DEGRADED* |
| fisherman | FISH | ItemFished | NeoForge+Fabric | ACTIVE |
| researcher | POKEMON_CAPTURED, DEX_ENTRY_ADDED | Cobblemon | NeoForge+Fabric | BLOCKED_BY_ENVIRONMENT** |
| breeder | EGG_CREATED, EGG_HATCHED | Cobblemon | NeoForge+Fabric | BLOCKED_BY_ENVIRONMENT** |
| trainer | TRAINER_BATTLE_WON | Cobblemon | NeoForge+Fabric | BLOCKED_BY_ENVIRONMENT** |
| pasture_keeper | PASTURE_TASK_COMPLETED | Cobblemon | NeoForge+Fabric | BLOCKED_BY_ENVIRONMENT** |
| paleontologist | FOSSIL_REVIVED | Cobblemon | NeoForge+Fabric | BLOCKED_BY_ENVIRONMENT** |
| raider | RAID_CLEARED | Cobblemon | NeoForge+Fabric | BLOCKED_BY_ENVIRONMENT** |

*Magician: Eventos de encantamento e pocoes precisam de adapters dedicados (nao implementados neste escopo)
**Pokemon: Dependente da integracao Cobblemon. Funcionam quando Cobblemon esta instalado.

## Migracoes Realizadas

1. Schema version: 2 -> 3
2. Wildcards removidos de todas as 17 profissoes default
3. Explorer: `"*"` convertido para `"default-reward"`
4. Backup automatico via `createBackup()` no reload
5. Validacao de wildcards em `validateAll()`: rejeita config com `"*"` em acoes economicas
6. Configuracoes antigas preservadas por migracao explicita

## Testes Executados

### Suite: JobsXpRefactorTest (25 testes)

**Miner (5 testes)**
- `minerGramaNoXp`: grama = 0, terra = 0, areia = 0, carvao = configurado
- `minerOreNotConfiguredZero`: minerio fora da config = 0
- `minerOrePorTagTemXp`: tag funciona
- `minerBlockPlacedNoXp`: bloco colocado = 0
- `minerNaturalOreHasXp`: minerio natural = valido

**Fazendeiro (7 testes)**
- `farmerConfigNoWildcard`: config sem wildcard nem default
- `farmerImmatureCropNoXp`: imaturo = 0
- `farmerMatureCropHasXp`: maduro = valido
- `farmerPlayerPlantedMatureAllowed`: plantado+maduro = ok
- `farmerPlayerPlacedImmatureBlocked`: plantado+imaturo = 0
- `farmerBreakBlockPlayerPlacedBlocked`: BREAK_BLOCK player-placed = 0
- `farmerPlaceAndBreakImmediateCaughtByRepeatGuard`: loop detectado

**Explorador (4 testes)**
- `explorerNoExplorerJobDoesNotConsumeDiscovery`: sem job = nao consome
- `explorerFirstDiscoveryRewardsOnce`: primeira = ok, segunda = 0
- `explorerFailureDoesNotConfirm`: falha = nao confirma
- `explorerBiomeAndCellDoNotCollide`: BIOME e CELL independentes

**Geral (9 testes)**
- `cancelledEventNoReward`, `creativeSpectatorNoReward`
- `fingerprintDedupPreventsDoubleReward`
- `twoLegitimateFastActionsGiveTwoRewards`
- `configValidatorRejectsWildcards`
- `explorerDefaultRewardWorks`
- `actionCooldownPreventsSpam`
- `jobActionContextMetadataJson`
- `provenanceTypeEvidence`
- `pipelineRejectsDuplicateActionId`
- `ruleEvaluatorNoMatchReturnsNoMatch`
- `allActionTypesParseable`
- `negativeCoordinatesConsistentGridCells`
- `loadingStateBlocksDiscoveryReservation`
- `playerDataPreservedOnReload`

### Resultado: Todos os 25 testes passam.

## Comandos Administrativos Adicionados

```text
/jobsadmin trace <player> on|off
    Ativa/desativa trace de acoes para o jogador, mostrando:
    RECEIVED, CLASSIFIED, REJECTED/ELIGIBLE, MATCHED_RULE,
    BASE_REWARD, MULTIPLIERS, FINAL_REWARD, APPLIED, RECEIPT

/jobsadmin explain block <registry_id>
    Mostra quais profissoes recompensam um bloco e com quais regras

/jobsadmin explain action <job> <action> <target>
    Mostra a regra exata, tags candidatas e default-reward para uma acao

/jobsadmin config status
    Mostra: caminho absoluto, schema version, hash, data, 
    qtd profissoes, regras exatas, tags, fallbacks, erros
```

## Riscos Remanescentes

1. **Magician**: Eventos de encantamento e brewing precisam de listeners dedicados. Atualmente usa USE_MAGIC mas sem eventos reais que disparem essa acao. Estado: DEGRADED.

2. **Piston/movimento de blocos**: Blocos movidos por pistao podem perder proveniencia. O `BlockProvenanceService` precisa de listeners para `PistonEvent` no NeoForge e equivalente no Fabric.

3. **Mod crops**: Cultivos de mods com propriedades customizadas podem nao ser detectados corretamente se nao usarem `age` como nome da propriedade. O fallback generico cobre a maioria, mas mods exoticos podem precisar de configuracao manual.

4. **Multi-block plants (bamboo, sugarcane)**: Colheita apenas do bloco superior pode nao detectar corretamente sem snapshot antes/depois. O `isValidHarvestTransition` existe mas precisa ser integrado ao listener.

5. **Fabric BlockPlace**: Fabric nao possui evento nativo de BlockPlace como NeoForge. Pode precisar de mixin adicional para rastrear colocacao de blocos.

6. **Race condition em MySQL**: Apesar de `INSERT OR IGNORE`/`INSERT IGNORE`, o fluxo reserve+confirm e assincrono. Duas threads podem reservar simultaneamente. O impacto e minimo (perda de uma descoberta), mas deve ser monitorado.

## Passo a Passo de Validacao Manual (Cobbleverse)

1. Entrar no servidor com NeoForge
2. `/jobsadmin config status` - verificar schema version 3
3. Escolher Minerador: `/jobs entrar miner`
4. Quebrar grama: verificar que $0 e XP 0 no action bar
5. Quebrar minerio de ferro natural: verificar recompensa correta
6. Colocar e quebrar minerio de ferro: verificar $0 (bloco colocado)
7. Trocar para Fazendeiro: `/jobs trocar farmer`
8. Plantar trigo, esperar crescer, colher: verificar recompensa
9. Colher trigo imaturo: verificar $0
10. `/jobsadmin trace <seu_nome> on` - ativar trace
11. Explorar bioma novo: verificar trace mostrando fluxo completo
12. Visitar mesmo bioma novamente: verificar NO_MATCHING_REWARD_RULE
13. Verificar `/jobsadmin explain block minecraft:grass_block` - sem recompensa
14. Verificar `/jobsadmin explain block minecraft:coal_ore` - recompensa do miner
15. Repetir testes no Fabric e verificar resultados identicos
16. Verificar que `/jobs info` mostra niveis e progresso preservados
17. Fazer `/jobsadmin reload` e verificar que progresso continua intacto
