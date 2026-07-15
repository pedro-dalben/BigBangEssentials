# Especificação Técnica de Integrações Cobbleverse (Jobs — Fase 5)

Este documento registra a arquitetura real das integrações, o modelo de health state machine dos bridges e o status operacional verificado de cada integração.

---

## 1. Arquitetura de Integração

Cada bridge implementa `OptionalJobsIntegration` com health state machine completa:

```
NOT_PROBED → MOD_NOT_INSTALLED | MOD_INSTALLED_API_NOT_FOUND | API_CLASS_NOT_FOUND | API_FOUND → SUBSCRIPTION_SUCCEEDED → ACTIVE | DEGRADED | ERROR | SHUTDOWN
```

### Contrato de integração

- `probeApi()` — inspeciona mod presente e classes de evento via reflexão. Retorna `IntegrationStatus`.
- `subscribeEvents()` — registra listener real no event bus comprovadamente encontrado. Retorna `SubscriptionResult` (nunca `void`).
- `shutdown()` — remove listener registrado, limpa estado.
- `getStatus()` — retorna status atualizado em tempo real (nunca cache stale).

### Regras críticas

1. `ACTIVE` só pode ser usado depois de comprovar inscrição bem-sucedida em evento real.
2. UUID de Pokémon nunca é gerado como fallback (`UUID.randomUUID()`). Sem UUID confiável, ação é rejeitada (fail-closed).
3. Exceções de handler atualizam `IntegrationStatus` para `DEGRADED` e registram `lastError`.
4. Inicialização é idempotente via `AtomicBoolean`. Chamar `initializeAll()` duas vezes não cria listeners duplicados.
5. Reload executa shutdown completo (limpa listeners) antes de re-probe.

---

## 2. Matriz de Compatibilidade Real

| Integração | Estado | Mod Detectado | Evento Real | Adapter | Ações Suportadas |
|:---|:---|:---|:---|:---|:---|
| `cobblemon_base` | API_FOUND (aguardando inscrição em runtime) | Cobblemon | `PokemonCapturedEvent` via `CobblemonEvents` | REFLECTIVE | POKEMON_CAPTURED, DEX_ENTRY_ADDED |
| `cobblemon_breeding` | Variável (API probe) | Cobblemon/Cobbreeding | Multi-candidato (ver tabela) | REFLECTIVE | EGG_CREATED (se Cobbreeding), EGG_HATCHED |
| `cobblemon_trainers` | API_FOUND | Cobblemon/RCTMod | `BattleVictoryEvent` via `CobblemonEvents` | REFLECTIVE | TRAINER_BATTLE_WON |
| `cobblemon_pasture` | MOD_NOT_INSTALLED | N/A | Nenhum evento registrado | NONE | PASTURE_TASK_COMPLETED (somente contrato) |
| `cobblemon_fossils` | MOD_NOT_INSTALLED | N/A | Nenhum evento registrado | NONE | FOSSIL_REVIVED (sem listener ativo) |
| `cobblemon_raids` | MOD_NOT_INSTALLED | N/A | Nenhum evento registrado | NONE | RAID_CLEARED (sem listener ativo) |

---

## 3. Detalhamento Técnico das Bridges

### 3.1 Cobblemon Base — Pesquisador Pokémon
- **Arquivo:** `CobblemonJobsBridge.java`
- **Evento:** `com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent`
- **Event bus:** `com.cobblemon.mod.common.api.events.CobblemonEvents` — field estático com tipo genérico compatível
- **Método de inscrição:** `subscribe(Class, Consumer)` via reflexão
- **Método de remoção:** `unsubscribe(Class, Consumer)` — suporte detectado dinamicamente
- **Extração de dados:** `getPokemon()`, `getPlayer()`, `getUuid()`, `getSpecies()` → `getName()`, `getForm()`, `isShiny()`, `getCaughtBall()`, `getTradeHistory()`, `getPersistentData()`
- **Fluxo:** evento → `handleCaptureEvent()` → extração fail-closed → `CaptureCorrelationService.processCapture()` → `JobAction.POKEMON_CAPTURED` + `JobAction.DEX_ENTRY_ADDED` (se primeira descoberta)
- **Anti-exploit:** Pokémon sem UUID (`extractUuidOrReject` retorna null) → ação rejeitada e auditada. Trade history não vazio → rejeitado. Admin spawned → rejeitado.
- **Deduplicação:** `JobActionReceiptRepository.reserveAction()` + UUID derivado de `"cap_" + playerId + "_" + pokemonUuid`

### 3.2 Breeding — Criador Pokémon
- **Arquivo:** `BreedingJobsBridge.java`
- **Eventos candidatos para hatch:** `EggHatchEvent`, `PokemonHatchedEvent`, `PokemonHatchedEvent` (alternativo)
- **Eventos candidatos para create:** `EggCreatedEvent`, `PokemonEggCreatedEvent`
- **Nota:** `EggHatchEvent` pode não existir em versões recentes do Cobblemon. O bridge testa múltiplos candidatos e assina apenas os encontrados.
- **Estratégia fallback:** Se nenhum evento for encontrado, bridge entra em `API_CLASS_NOT_FOUND` e o Job permanece indisponível.
- **Fluxo:** `EggLifecycleService.processEggHatched()` / `processEggCreated()` → `JobAction.EGG_HATCHED` / `JobAction.EGG_CREATED`
- **Deduplicação:** `EggLifecycleService` mantém mapa `processedEggs` por `eggUuid`.

### 3.3 Trainers — Treinador da Liga
- **Arquivo:** `TrainerJobsBridge.java`
- **Evento:** `com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent`
- **Diferenciação PvP:** verifica `isPvP()` no objeto da batalha. Se true, não processa o evento.
- **Deduplicação:** chave composta `playerUUID + trainerId + battleId` em `ConcurrentHashMap.newKeySet()`. Não usa minuto do sistema.
- **Action ID:** `UUID.nameUUIDFromBytes("battle_" + playerId + "_" + trainerId + "_" + battleId)` — determinístico e repetível.
- **Tiers de treinador:** `TrainerMappingService` mapeia para `GYM_LEADER`, `ELITE_FOUR`, `CHAMPION`, `TRAINER_COMMON`.
- **Cooldown:** 24h para líderes/E4/campeão, 1h para trainers comuns.

### 3.4 Pasture — Cuidador de Pasture
- **Arquivo:** `PastureJobsBridge.java`
- **Estado:** `MOD_NOT_INSTALLED` — não existe mod de Pasture detectado no modpack.
- **Modo de contrato:** `PastureCollectionService.processManualCollection()` aceita apenas `eventSource = "manual"` ou `"contract_delivery"`.
- **Sem listener ativo:** bridge não registra eventos. Progressão via contratos de entrega no menu.
- **Painel reflete corretamente:** sem falso `ACTIVE`.

### 3.5 Fósseis — Paleontólogo
- **Arquivo:** `FossilJobsBridge.java`
- **Estado:** `MOD_NOT_INSTALLED` — mod de fósseis não detectado.
- **Serviço de deduplicação:** `FossilProcessDeduplicationService.processFossilRevived()` — disponível para chamadas externas quando mod for instalado.
- **Sem listener ativo:** bridge não registra eventos.

### 3.6 Raids — Especialista em Raids
- **Arquivo:** `RaidDensJobsBridge.java`
- **Estado:** `MOD_NOT_INSTALLED` — mod de raids não detectado.
- **Serviço de deduplicação:** `RaidDeduplicationService.processRaidCleared()` — disponível quando mod for instalado.
- **Sem listener ativo:** bridge não registra eventos. Raider job indisponível.

---

## 4. Pipeline Central

Toda ação Pokémon é processada pelo mesmo pipeline:

```
Event → Bridge Handler → JobAction (normalizado, ID determinístico)
  → JobActionProcessor.process()
    → JobActionReceiptRepository.reserveAction() [idempotência]
    → JobActionValidator.validate() [anti-exploit, AFK, spam, PvP]
    → PokemonJobActionValidator.validatePokemonAction() [admin spawn, trade, passive farm]
    → JobEligibilityResolver [jobs elegíveis do jogador]
    → JobRuleEvaluator [regras de recompensa por target/espécie]
    → JobRewardCalculator [cálculo com multiplicadores e daily limits]
    → JobRewardApplier [aplicação de XP e moedas]
```

---

## 5. Diagnóstico Administrativo

Comando `/jobsadmin integrations` mostra por integração:
- Estado atual (colorido)
- Mod ID detectado e versão
- Adapter selecionado
- Classe do evento assinado
- Event bus utilizado
- Status da inscrição (SUBSCRIBED/FAILED/NOT_SUBSCRIBED)
- Total de eventos recebidos / aceitos / rejeitados
- Timestamp do último evento e último sucesso
- Último erro registrado (se houver)

Comando `/jobsadmin integrations probe` executa re-probe seguro de todas integrações sem duplicar listeners.

Comando `/jobsadmin audit <jogador>` mostra logs de auditoria de ações Pokémon do jogador.

---

## 6. Relação Job → Integração

| Job | Integração | Bloqueado se |
|:---|:---|:---|
| `researcher` | `cobblemon_base` | Cobblemon ausente |
| `breeder` | `cobblemon_breeding` | Evento de hatch não encontrado |
| `trainer` | `cobblemon_trainers` | BattleVictoryEvent não encontrado |
| `pasture_keeper` | `cobblemon_pasture` | Sempre (contrato apenas) |
| `paleontologist` | `cobblemon_fossils` | Mod de fósseis ausente |
| `raider` | `cobblemon_raids` | Mod de raids ausente |

---

## 7. Testes

- `IntegrationBridgeTest` — 53 testes cobrindo:
  - Transições de health state (NOT_PROBED → ACTIVE → DEGRADED → SHUTDOWN)
  - SubscriptionResult (success, failed, modNotInstalled, apiNotFound, eventBusNotFound)
  - IntegrationStatus campos ricos de diagnóstico
  - JobActionType parsing para todas ações Pokémon
  - Idempotência de ações (reserveAction, isAlreadyProcessedOrProcessing)
  - Deduplicação de captura (action ID determinístico)
  - Deduplicação de Dex (case insensitive, primeira vez apenas)
  - PokemonJobActionValidator (admin spawn, trade, PvP, passive farm, manual pasture)
  - IDs de ação estáveis e determinísticos para todas ações Pokémon
  - TrainerCooldownService e TrainerMappingService
  - Integridade do registry (todas 6 integrações registradas)
  - Probe seguro do registry

- `PokemonJobsTest` — testes de regras wildcard e anti-exploit
- `JobActionPipelineTest` — parsing, context builder, idempotência, validação anti-exploit
- `JobsSystemTest` — integração completa com JobsManager, XP, daily limits, permissions
