# Auditoria Independente do Sistema Gems

**Data:** 27/06/2026
**Auditor:** OpenCode Independent Audit Pipeline
**SHA Auditado:** `ac4d4a829b73aaf97d78fd7b93bd51221fdf5092`
**Branch:** `master`
**Status da árvore:** Limpa (sem mudanças locais)

---

## 1. Baseline

| Item | Resultado |
|---|---|
| `git status --short` | Nenhuma mudança |
| `git rev-parse HEAD` | `ac4d4a829b73aaf97d78fd7b93bd51221fdf5092` |
| `git branch` | `master` |
| `git diff --check` | Sem warned de whitespace |
| `./gradlew clean test build` | **BUILD SUCCESSFUL** (12s, 25 ações) |
| Java | OpenJDK 64-Bit Server VM (LTS) |
| Loader | Fabric + NeoForge |
| Dependências novas | Nenhuma (usa apenas GSON nativo) |
| Nº total de arquivos Gems | **41** (19 main + 15 test + 5 wiki + 1 integration contract + 1 audit) |
| Nº de testes | **15 classes de teste** (~35-40 métodos individuais) |

### Commits relacionados a Gems

```
ac4d4a82 docs: add gems independent audit review and wiki system pages
037bafb8 feat(gems): implement copy-on-write transaction model and crash failpoints
5bcc5bad test: add comprehensive gems test suite and isolation
ac5bc86f feat: add gems balances commands ledger and placeholders
2eefc405 feat: add configurable gems wallet persistence and recovery
0eca9a2d docs: define gems wallet architecture and Regions integration contract
```

---

## 2. Inventário Técnico Completo

### Mapa de arquivos

| Camada | Classe/Arquivo | Responsabilidade | Estado Mutável | Persistência | Thread | Risco |
|---|---|---|---|---|---|---|
| **API** | `GemsService` | Contrato público para mods externos | Imutável | Nenhuma | Chamadora | Baixo |
| **API** | `BigBangEssentialsApi` | Ponto de entrada da API do mod | Imutável | Nenhuma | Chamadora | Baixo |
| **API** | `GemOperationResult` | Record de resultado (success+failure+view) | Imutável (record) | Nenhuma | N/A | N/A |
| **API** | `GemOperationFailure` | Enum de 15 códigos de falha | Imutável | Nenhuma | N/A | N/A |
| **API** | `GemReservationResult` | Resultado especializado para reserve | Imutável (record) | Nenhuma | N/A | N/A |
| **API** | `GemBalanceView` | View de saldo (total/held/available) | Imutável (record) | Nenhuma | N/A | N/A |
| **API Requests** | `GemCreditRequest` | Request de crédito | Imutável (record) | Nenhuma | N/A | N/A |
| **API Requests** | `GemDebitRequest` | Request de débito | Imutável (record) | Nenhuma | N/A | N/A |
| **API Requests** | `GemSetBalanceRequest` | Request de set admin | Imutável (record) | Nenhuma | N/A | N/A |
| **API Requests** | `GemReservationRequest` | Request de reserva c/ lease, idempotencyKey | Imutável (record) | Nenhuma | N/A | N/A |
| **API Requests** | `GemCaptureRequest` | Request de captura c/ idempotencyKey | Imutável (record) | Nenhuma | N/A | N/A |
| **API Requests** | `GemReleaseRequest` | Request de release **SEM idempotencyKey** | Imutável (record) | Nenhuma | N/A | **Médio** |
| **API Requests** | `GemRenewRequest` | Request de renovação de lease | Imutável (record) | Nenhuma | N/A | N/A |
| **Core** | `GemsManager` | Singleton central: estado, lock, operações, recovery, cleanup | `currentState`, `idempotencyRegistry`, `shuttingDown`, `dataIntegrityError` | Delegada a `GemsPersistence` | `ReentrantReadWriteLock` + scheduler dedicado | Copy-on-Write implementado |
| **Domain** | `GemReservation` | Reserva individual com status, lease, timestamps | `status`, `expiresAt`, `capturedAt`, `releasedAt` | Nenhuma | Manager lock | Deep copy via `copy()` |
| **Domain** | `GemReservationStatus` | Enum: ACTIVE, CAPTURED, RELEASED, EXPIRED | Imutável | Nenhuma | N/A | N/A |
| **Domain** | `GemTransaction` | Record de transação para ledger | Imutável (record) | Nenhuma | N/A | N/A |
| **Domain** | `GemTransactionType` | Enum de tipos de transação | Imutável | Nenhuma | N/A | N/A |
| **Domain** | `GemCurrencyDescriptor` | Metadados da moeda (símbolo, nome) | Imutável | Nenhuma | N/A | N/A |
| **Domain** | `GemBalanceView` | View de saldo | Imutável (record) | Nenhuma | N/A | N/A |
| **Persistence** | `GemsPersistence` | I/O de arquivos: state + ledger | Stateless (exceto config cache) | `gems_state.json`, `gems_transactions.jsonl` | `synchronized` nos métodos | Baixo (stateless) |
| **Persistence** | `GemsState` | POJO de estado serializável | `balances`, `reservations` | `gems_state.json` | Nenhuma | N/A |
| **Persistence** | `GemsPersistenceFailpoint` | Enum de pontos de falha para teste | Imutável | Nenhuma | N/A | **Apenas 8/12 failpoints** |
| **Config** | `GemConfig` | Config serializável em `gems.json` | Mutável via load | `gems.json` | Nenhuma | N/A |
| **Config** | `GemConfigValidator` | Validação de config | Imutável | Nenhuma | N/A | N/A |
| **Service** | `GemsServiceImpl` | Ponte entre API e Manager | Nenhum | Nenhuma | Chamadora | N/A |
| **Command** | `GemsCommand` | Comando `/gems` e `/gemas` (Brigadier) | Nenhum | Nenhuma | Server thread | N/A |
| **Event** | `GemBalanceChangedEvent` | Evento de mudança de saldo | Imutável | Nenhuma | Manager lock | N/A |
| **Event** | `GemReservationCreatedEvent` | Evento de criação de reserva | Imutável | Nenhuma | Manager lock | N/A |
| **Event** | `GemReservationCapturedEvent` | Evento de captura | Imutável | Nenhuma | Manager lock | N/A |
| **Event** | `GemReservationReleasedEvent` | Evento de release | Imutável | Nenhuma | Manager lock | N/A |
| **Event** | `GemReservationExpiredEvent` | Evento de expiração | Imutável | Nenhuma | Manager lock | N/A |

### Isolamento de dependências (verificado)

- Gems **não importa** `BigBangRegions`, `Region`, `RegionResizeService`, `PlotSlot` ✓
- Gems **não importa** `Vault` ou `net.milkbowl.vault` ✓
- Gems **não importa** `EconomyManager` de Coins, `balances.json`, `transactions.json` ✓
- Chave técnica fixa: `bigbangessentials:gems` (validada via `GemConfigValidator`) ✓
- `technicalId` é imutável e validado em runtime ✓

---

## 3. Auditoria de API Pública

### Interface `GemsService`

| Método | Retorno | Validações | Idempotente | Observação |
|---|---|---|---|---|
| `descriptor()` | `GemCurrencyDescriptor` | N/A | N/A | Apenas leitura |
| `getBalance(UUID)` | `GemBalanceView` | N/A | N/A | Apenas leitura |
| `hasAvailable(UUID, long)` | `boolean` | amount>=0 implícito | N/A | Apenas leitura |
| `credit(GemCreditRequest)` | `GemOperationResult` | amount>0, source, purpose | Sim (idempotencyKey) | |
| `debit(GemDebitRequest)` | `GemOperationResult` | amount>0, source, purpose, available OK | Sim (idempotencyKey) | |
| `setBalance(GemSetBalanceRequest)` | `GemOperationResult` | amount>=0, >=held, <=max, source, purpose | Não (sem key) | Uso admin |
| `reserve(GemReservationRequest)` | `GemReservationResult` | amount>0, source, purpose, lease válido | Sim (idempotencyKey) | |
| `capture(GemCaptureRequest)` | `GemOperationResult` | reservation válida, transição válida | Sim (idempotencyKey + status) | |
| `release(GemReleaseRequest)` | `GemOperationResult` | reservation válida, transição válida | **Sim por status** mas **sem idempotencyKey** | **ACHADO #5** |
| `renew(GemRenewRequest)` | `GemOperationResult` | reservation ACTIVE, lease válido | Não (sem key) | |
| `findReservation(UUID)` | `Optional<GemReservation>` | N/A | N/A | Apenas leitura |
| `findReservationByIdempotencyKey(String)` | `Optional<GemReservation>` | N/A | N/A | Apenas leitura |
| `getHistory(UUID, int, int)` | `List<GemTransaction>` | N/A | N/A | Apenas leitura |

### Achados da API

1. ✅ Todos os amounts usam `long` - sem `double` ou `float`
2. ✅ Amounts são validados como `> 0` (credit/debit/reserve) ou `>= 0` (set)
3. ✅ `source` e `purpose` são obrigatórios e validados (lowercase, digits, `-`, `_`, max 64 chars)
4. ✅ `idempotencyKey` é suportado nos métodos críticos (credit, debit, reserve, capture)
5. ✅ Falhas usam `GemOperationFailure` enum com código estruturado
6. ✅ Nenhum stacktrace vaza na API - exceptions são capturadas e convertidas em `GemOperationFailure`
7. ✅ `capture` é idempotente (já capturado retorna success)
8. ✅ `release` é idempotente por status (já released retorna success)
9. ❌ **`release()` não aceita `idempotencyKey`** - não há como o caller garantir idempotência via chave
10. ❌ **`renew()` não aceita `idempotencyKey`** - idem
11. ⚠️ `metadata` não tem limite de tamanho explícito na API (embora seja `Map<String, String>`)

---

## 4. Durabilidade e Ledger

### Estratégia Implementada: State authoritative + ledger reconciliável

**Implementa a Estratégia A** do contrato de auditoria, com Copy-on-Write.

### Ordem real de persistência

```
1. stateLock.writeLock().lock()
2. Clone profundo de GemsState (cloneState + GemReservation.copy)
3. Aplica mutação no clone (nextState)
4. persistence.saveState(nextState):
   4a. BEFORE_WRITE_TEMP failpoint
   4b. Incrementa revision
   4c. Serializa JSON → gems_state.json.tmp
   4d. AFTER_WRITE_TEMP failpoint
   4e. BEFORE_ATOMIC_MOVE failpoint
   4f. Files.move(tmp → state, ATOMIC_MOVE | REPLACE_EXISTING)
       → fallback: Files.copy + delete tmp
   4g. AFTER_ATOMIC_MOVE failpoint
   4h. Backup opcional
5. BEFORE_CACHE_SWAP failpoint
6. currentState = nextState (swap da referência)
7. appendTransaction ao ledger (gems_transactions.jsonl)
8. save idempotency registry (em cache)
9. BEFORE_EVENT_PUBLISH failpoint
10. postEventSafely (evento de domínio)
11. stateLock.writeLock().unlock()
```

### Fonte de verdade

**`gems_state.json`** é a fonte de verdade absoluta. **`gems_transactions.jsonl`** é audit log, não WAL.

### Recovery no boot

1. Carrega `gems_state.json`
2. Valida `schemaVersion == 1`
3. Itera reservas: se ACTIVE + expirada → EXPIRED, registra no ledger
4. Recalcula `heldBalance` por jogador a partir de reservas ACTIVE não expiradas
5. Valida: nenhum saldo negativo, nenhum held > total
6. Se `dataIntegrityError`, bloqueia mutações
7. Reconstrói `idempotencyRegistry` a partir do ledger + reservas ativas

### Achados de durabilidade

1. ✅ Copy-on-Write garante que alterações em memória só persistem após escrita em disco bem-sucedida
2. ✅ `Files.move` com `ATOMIC_MOVE` previne estado parcial
3. ✅ Ledger trimming preserva últimas N entradas sem perda de integridade
4. ✅ Corrupted state file é preservado via backup antes de desabilitar Gems
5. ⚠️ Ledger trimming descarta entradas antigas - não há reconciliação de ledger (transações antigas perdidas)
6. ⚠️ Ledger NÃO é usado para recovery de estado - só para auditoria/histórico

---

## 5. Crash Injection

### Failpoints definidos (8 de 12 requeridos)

| # | Failpoint | Testado? | Cenário de crash |
|---|---|---|---|
| 1 | `BEFORE_WRITE_TEMP` | ✅ | Antes de escrever arquivo temporário |
| 2 | `AFTER_WRITE_TEMP` | ❌ | Após escrever temp, antes de atomic move |
| 3 | `BEFORE_ATOMIC_MOVE` | ❌ | Após temp escrito, antes de renomear |
| 4 | `AFTER_ATOMIC_MOVE` | ❌ | Após atomic move, antes de swap |
| 5 | `BEFORE_APPEND_LEDGER` | ✅ | Após state salvo + cache swap, antes do log |
| 6 | `AFTER_APPEND_LEDGER` | ❌ | Após ledger, antes de evento |
| 7 | `BEFORE_CACHE_SWAP` | ✅ | Após state salvo em disco, antes de swap de referência |
| 8 | `BEFORE_EVENT_PUBLISH` | ❌ | Após tudo, antes de publicar evento |

### Falta: 4 failpoints de cenário

O contrato pede 12 failpoints. Faltam os seguintes cenários:

- 9. Durante `reserve` (combined: beforeWriteTemp + reserve flow)
- 10. Durante `capture` (combined: beforeWriteTemp + capture flow)
- 11. Durante `release` (combined: beforeWriteTemp + release flow)
- 12. Durante `expiração` (cleanup task failure)

### Cobertura de testes de crash injection

- `GemCrashInjectionTest`: 3 cenários testados (BEFORE_WRITE_TEMP, BEFORE_CACHE_SWAP, BEFORE_APPEND_LEDGER)
- Cobertura: **3 de 8 failpoints testados** (38%)
- Nenhum teste de `AFTER_WRITE_TEMP`, `BEFORE_ATOMIC_MOVE`, `AFTER_ATOMIC_MOVE`, `AFTER_APPEND_LEDGER`, `BEFORE_EVENT_PUBLISH`
- Nenhum teste de crash durante capture (após reserve) com recovery completo
- Nenhum teste de crash durante release
- Nenhum teste de crash durante expiração automática

---

## 6. Concorrência e Atomicidade

### Testes de concorrência existentes

| Cenário | Status | Resultado |
|---|---|---|
| 5 threads reservando 30 de 100 disponíveis (só cabem 3) | ✅ Testado | 3 success, 2 fail, held=90 |
| Capture + Release simultâneos | ✅ Testado | Exatamente 1 succeed, 1 fail |
| 4 captures concorrentes (idempotência) | ✅ Testado | Todos success, 1 deduct |
| 4 releases concorrentes (idempotência) | ✅ Testado | Todos success, 1 restore |

### Cenários NÃO testados (contrato pede 12)

| # | Cenário | Status |
|---|---|---|
| 1 | Duas reservas simultâneas, saldo insuficiente para ambas | ✅ |
| 2 | `reserve` e `debit` simultâneos | ❌ |
| 3 | `reserve` e `admin take` simultâneos | ❌ |
| 4 | `capture` e `release` simultâneos | ✅ |
| 5 | `capture` repetido em threads diferentes | ✅ |
| 6 | `release` repetido em threads diferentes | ✅ |
| 7 | `renew` e `expire` simultâneos | ❌ |
| 8 | `cleanup` e `capture` simultâneos | ❌ |
| 9 | `shutdown` durante `reserve` | ❌ |
| 10 | `shutdown` durante `capture` | ❌ |
| 11 | Duas calls externas com mesmo `idempotencyKey` | ✅ (via idempotency test) |
| 12 | Duas calls com mesmo `idempotencyKey` e payload diferente | ✅ (via idempotency test) |

### Mecanismo de concorrência

- `ReentrantReadWriteLock(true)` - leituras não bloqueiam entre si, escritas são exclusivas
- `ConcurrentHashMap` para idempotencyRegistry (leitura sem lock para check rápido)
- Toda mutação adquire `writeLock()` - serializa escritas
- `ScheduledExecutorService` para cleanup de expiradas (single thread)
- `shuttingDown` flag checked early - previne novas operações

---

## 7. Contrato BigBang Regions

### Fluxo validado

O fluxo completo de 10 passos do Regions → Gems foi validado:

1. ✅ Regions gera `operationId` persistido
2. ✅ Regions usa `idempotencyKey` estável (`bigbangregions:resize:<regionId>:<operationId>`)
3. ✅ Regions chama `reserve()` → retorna `reservationId`
4. ✅ Regions grava `PAYMENT_RESERVED`
5. ✅ Regions aplica resize
6. ✅ Regions grava `RESIZE_APPLIED`
7. ✅ Regions chama `capture()` → apenas uma cobrança
8. ✅ Regions grava `PAYMENT_CAPTURED`

### Casos de recovery validados

| Cenário | Resultado |
|---|---|
| Crash após reserve, antes de PAYMENT_RESERVED | Regions não tem estado → retry reserve (idempotente) |
| Crash após PAYMENT_RESERVED, antes de resize | Regions retoma do checkpoint → faz resize → capture |
| Crash após resize, antes de capture | Regions retoma → capture (idempotente) |
| Crash após capture, antes de PAYMENT_CAPTURED | Regions retoma → capture retry (idempotente) |
| Reserva expirada durante operação | Reserve retry → pode falhar se saldo mudou |
| Lease renovada | `renew()` extende lease |

### Contrato de Lease

| Parâmetro | Valor | Configurável |
|---|---|---|
| `defaultLeaseSeconds` | 900 (15 min) | Sim |
| `maxLeaseSeconds` | 3600 (1 hora) | Sim |
| `cleanupIntervalSeconds` | 60 | Sim |
| `allowExternalRenewal` | true | Sim |

### Responsabilidades

| Estado | Dono | Persistência |
|---|---|---|
| `PAYMENT_PENDING` | Regions | Regions (local) |
| `PAYMENT_RESERVED` + `reservationId` | Regions | Regions (local) |
| `RESIZE_APPLIED` | Regions | Regions (local) |
| `PAYMENT_CAPTURED` | Regions | Regions (local) |
| `reservation ACTIVE` | Essentials | `gems_state.json` |
| `reservation CAPTURED` | Essentials | `gems_state.json` |
| `reservation RELEASED` | Essentials | `gems_state.json` |

### Achado: Contrato Regions depende de `release()` SEM `idempotencyKey`

O `GemReleaseRequest` não possui `idempotencyKey`. Se Regions crashar após chamar `release()` mas antes de persistir, e retentar, não há garantia de idempotência por chave. Atualmente o `release()` verifica status (`already released` retorna success), então é funcionalmente idempotente, mas o contrato de API não explicita isso.

---

## 8. Comandos e Permissões

### Comandos implementados

| Comando | Permissão | Status |
|---|---|---|
| `/gems` (self) | `bigbangessentials.gems.balance` | ✅ |
| `/gems balance [player]` | `bigbangessentials.gems.balance` / `.balance.others` | ✅ |
| `/gems history [page]` | `bigbangessentials.gems.history` | ✅ |
| `/gems admin give` | `bigbangessentials.gems.admin.give` | ✅ |
| `/gems admin take` | `bigbangessentials.gems.admin.take` | ✅ |
| `/gems admin set` | `bigbangessentials.gems.admin.set` | ✅ |
| `/gems admin reset` | `bigbangessentials.gems.admin.reset` | ✅ |
| `/gems admin balance` | `bigbangessentials.gems.admin.balance` | ✅ |
| `/gems admin history` | `bigbangessentials.gems.admin.history` | ✅ |
| `/gems admin reservations` | `bigbangessentials.gems.admin.reservations` | ✅ |
| `/gems admin reservation inspect` | `bigbangessentials.gems.admin.reservations` | ✅ |
| `/gems admin reservation release <id> confirm` | `bigbangessentials.gems.admin.release` | ✅ |
| `/gems admin verify` | `bigbangessentials.gems.admin.verify` | ✅ |
| `/gems admin repair confirm` | `bigbangessentials.gems.admin.repair` | ✅ |
| `/gems admin reload` | `bigbangessentials.gems.admin.reload` | ✅ |
| `/gemas` (alias) | Mesma que `/gems` | ✅ |

### Testes de comando

- Apenas `GemCommandAuthorizationTest` testa registro do comando (não testa execução real)
- Não há testes para: amount inválido, amount decimal, permissão negada, autocomplete
- `GemAmountParsingTest` testa validação de amount via API (não via comando)

### Issues encontradas

1. ⚠️ `executeAdminReset` usa `fallbackStarting = 0` hardcoded (ignora config.startingBalance)
2. ⚠️ Nenhum teste de comando executa o handler real - só testa registro do nó
3. ✅ Repair requer `confirm` literal
4. ✅ Release de reserva requer `confirm` literal

### Regressão Coins

- Gems não altera comportamento de Coins ✓
- Coins continua via Vault ✓
- Arquivos de Coins (`balances.json`, `transactions.json`) não são tocados ✓
- Nenhum import cruzado entre economia de Coins e Gems ✓

---

## 9. Testes Existentes (Cobertura)

| Teste | O que cobre |
|---|---|
| `GemApiContractTest` | API via `BigBangEssentialsApi`, credit via service |
| `GemBalanceServiceTest` | Credit, debit, insufficient, maxBalance |
| `GemAmountParsingTest` | Zero/negative amounts rejeitados |
| `GemConfigValidationTest` | Validação de config (techId, balanços, leases) |
| `GemFormattingTest` | Formatação de valores |
| `GemLedgerPersistenceTest` | Ledger registra credit+debit corretamente |
| `GemReservationStateMachineTest` | Reserve, capture, release, invalid transitions |
| `GemReservationIdempotencyTest` | Idempotência de credit e reserve |
| `GemReservationConcurrencyTest` | 4 cenários de concorrência |
| `GemReservationRecoveryTest` | Recovery com reservas expiradas/ativas |
| `GemCrashInjectionTest` | 3 cenários de crash injection |
| `GemExternalIntegrationContractTest` | Fluxo completo Regions (reserve→capture) |
| `GemCommandAuthorizationTest` | Registro de comandos |
| `GemPlaceholderTest` | Placeholders básicos |

---

## 10. Achados e Correções

### Achados Existentes (do commit 037bafb8, já corrigidos)

| # | Severidade | Problema | Solução |
|---|---|---|---|
| 1 | CRITICAL | Divergência cache/disco sob falha de I/O | Copy-on-Write implementado |
| 2 | HIGH | Liberação manual sem confirmação | Literal "confirm" obrigatório |
| 3 | MEDIUM | Idempotência com payload divergente | IDEMPOTENCY_CONFLICT implementado |

### Achados Novos (desta auditoria)

| # | Severidade | Problema | Local |
|---|---|---|---|
| **A4** | **HIGH** | **Crash injection coverage insuficiente**: apenas 3/8 failpoints testados | `GemCrashInjectionTest.java` |
| **A5** | **HIGH** | **Failpoints incompletos**: apenas 8 definidos, contrato pede 12 (faltam reserve/capture/release/expiration scenario-level) | `GemsPersistenceFailpoint.java` |
| **A6** | **MEDIUM** | **`GemReleaseRequest` sem `idempotencyKey`**: não há como caller garantir idempotência via chave para release | `GemReleaseRequest.java` |
| **A7** | **MEDIUM** | **Concorrência incompleta**: apenas 4/12 cenários testados | `GemReservationConcurrencyTest.java` |
| **A8** | **LOW** | **`GemRenewRequest` sem `idempotencyKey`**: renovação não é idempotente por chave | `GemRenewRequest.java` |
| **A9** | **LOW** | **`executeAdminReset` usa `fallbackStarting = 0` hardcoded**: ignora config.startingBalance | `GemsCommand.java:479` |
| **A10** | **LOW** | **Nenhum teste de execução real de comando**: só testa registro do nó Brigadier | `GemCommandAuthorizationTest.java` |
| **A11** | **LOW** | **Documentação existente desatualizada**: audit.md antigo referenciava SHA `037bafb8` e já declarava APPROVED | `docs/reviews/gems-independent-audit.md` (corrigido agora) |

---

## 11. Veredito Final

### Critérios de aprovação

| Critério | Status | Evidência |
|---|---|---|
| `./gradlew clean test build` passa | ✅ | BUILD SUCCESSFUL |
| API pública não importa BigBang Regions | ✅ | Grep confirmado |
| Coins não tiveram regressão | ✅ | Nenhum shared state |
| Vault continua somente para Coins | ✅ | Nenhum import Vault em Gems |
| Gems usa apenas inteiros | ✅ | Todos `long` |
| Gems não permite saldo negativo | ✅ | Validado: amount>0, set>=0, allowNegativeBalances=false |
| reserve, capture, release são idempotentes | ✅ | Sim (capture/release por status, reserve por key) |
| Concorrência não permite gastar acima do availableBalance | ✅ | writeLock + validação |
| Restart preserva estado corretamente | ✅ | Recovery recalcula held + expira |
| Crash injection não causa perda nem duplicação | ⚠️ **Parcial** | Apenas 3/8 failpoints testados |
| Durabilidade documentada corretamente | ✅ | State authoritative + CoW |
| Ledger e state possuem recuperação consistente | ✅ | Recovery no boot |
| Reservas expiradas tratadas corretamente | ✅ | Cleanup task + recovery |
| BigBang Regions possui contrato de retry claro | ✅ | `docs/integrations/bigbangregions-gems-api.md` |
| Sem reflection, arquivo ou banco compartilhado | ✅ | Verificado |
| Testes manuais executados | ❌ **Não executado** | Sem servidor real disponível |
| Nenhum achado CRITICAL ou HIGH aberto | ❌ **A4 e A5 são HIGH abertos** | Crash injection coverage |

### Decisão

```txt
GEMS_API_APPROVED_WITH_REQUIRED_FIXES
```

### Motivação

O sistema tem uma base sólida: Copy-on-Write, idempotência, locks, API limpa, isolamento de Coins e Vault, recovery funcional. Porém, dois achados **HIGH** impedem a aprovação irrestrita:

1. **A4** (Crash injection coverage insuficiente) - a resiliência declarada não está totalmente coberta por testes automatizados
2. **A5** (Failpoints incompletos) - o contrato pede 12 pontos de falha, só 8 implementados

### Correções requeridas antes de APPROVED_FOR_REGIONS_INTEGRATION

1. Adicionar testes de crash injection para os 5 failpoints não testados (AFTER_WRITE_TEMP, BEFORE_ATOMIC_MOVE, AFTER_ATOMIC_MOVE, AFTER_APPEND_LEDGER, BEFORE_EVENT_PUBLISH)
2. Adicionar 4 failpoints de cenário (DURING_RESERVE, DURING_CAPTURE, DURING_RELEASE, DURING_EXPIRATION)
3. Testar crash durante capture (reserve existente + crash no capture)
4. Testar crash durante release
5. Testar crash durante expiração automática

### Correções recomendadas (MEDIUM/LOW)

6. Adicionar `idempotencyKey` a `GemReleaseRequest`
7. Adicionar `idempotencyKey` a `GemRenewRequest`
8. Adicionar 8 cenários de concorrência faltantes
9. Corrigir `executeAdminReset` para usar `startingBalance` do config
10. Adicionar testes de execução real de comandos

---

## Checklist final

- [x] `./gradlew clean test build` passa
- [x] API pública não importa BigBang Regions
- [x] Coins não tiveram regressão
- [x] Vault continua somente para Coins
- [x] Gems usa apenas inteiros
- [x] Gems não permite saldo negativo
- [x] reserve, capture e release são idempotentes
- [x] Concorrência não permite gastar acima do availableBalance
- [x] Restart preserva estado corretamente
- [ ] Crash injection não causa perda nem duplicação (Parcial - cobertura insuficiente)
- [x] Durabilidade está documentada corretamente
- [x] Ledger e state possuem recuperação consistente
- [x] Reservas expiradas são tratadas corretamente
- [x] BigBang Regions possui contrato de retry claro
- [x] Não existe acesso por reflection, arquivo compartilhado ou banco compartilhado
- [ ] Testes manuais foram executados (Não executado - sem servidor)
- [ ] Não existem achados CRITICAL ou HIGH abertos (2 HIGH abertos: A4, A5)
