# Gems P0 Remediation Plan

**SHA Inicial:** `b8bb0dd4`
**Branch:** `master`
**Baseline:** `./gradlew clean test build` — BUILD SUCCESSFUL (após correção de VipCommand)

---

## Tabela de Remediação

| ID | Severidade (Original) | Problema | Arquivos Afetados | Plano | Testes Exigidos | Status |
| -- | -------------------- | -------- | ----------------- | ----- | --------------- | ------ |
| A4 | HIGH | Crash injection coverage insuficiente | `GemCrashInjectionTest.java`, `GemsManager.java` | Já corrigido: 12 failpoints implementados e testados em múltiplas operações | Todos os 12 failpoints com restart+recovery | FIXED |
| A5 | HIGH | Failpoints incompletos | `GemsPersistenceFailpoint.java` | Já corrigido: enum com 12 valores, incluindo BEFORE/AFTER_IDEMPOTENCY_REGISTRY_UPDATE | Verificação de enum com 12 constantes | FIXED |
| A6 | MEDIUM | GemReleaseRequest sem idempotencyKey | `GemReleaseRequest.java` | Já corrigido: record já contém idempotencyKey, source, purpose, externalReference, metadata | Testes de release idempotente via chave | FIXED |
| A7 | MEDIUM | GemRenewRequest sem idempotencyKey | `GemRenewRequest.java` | Já corrigido: record já contém idempotencyKey | Testes de renew idempotente via chave | FIXED |
| A8 | MEDIUM | Concorrência incompleta | `GemReservationConcurrencyTest.java` | 11/12 cenários implementados. Faltante: cleanup concorrente com capture | Cenário #8: cleanup + capture concorrentes | IN_PROGRESS |
| A9 | MEDIUM | Admin reset usa fallbackStarting = 0 | `GemsCommand.java` | Já corrigido: usa `getConfig().balances.startingBalance` | Testes com startingBalance != 0 | FIXED |
| A10 | MEDIUM | Nenhum teste de comando real | `GemsCommand.java`, testes novos | Criar `GemCommandExecutionTest.java` com execução real via API (Brigadier requer Minecraft, testar lógica de serviço) | give, take, set, reset, reservations, release, verify, repair, reload | OPEN |
| A12 | HIGH | Idempotency registry não persiste após restart | `GemsState.java`, `GemsManager.java` | Adicionar `idempotencyRecords` ao `GemsState`. Persistir em todas as mutações. Carregar no recover(). | Credit/debit duplicado após restart com mesma key retorna idempotent_success | OPEN |

---

## Detalhamento dos Achados AINDA ABERTOS

### A12 — Idempotência Persistida (HIGH)

**Problema:** `IdempotencyRecord` existe apenas em `ConcurrentHashMap<String, IdempotencyRecord> idempotencyRegistry` em memória. Se o servidor crashar após `saveState` mas antes de `appendTransaction`, o ledger não contém a transação e o registry é perdido. Uma chamada externa com mesmo `idempotencyKey` seria processada novamente, gerando duplicação.

**Solução:** Adicionar `Map<String, IdempotencyPersistedRecord> idempotencyRecords` ao `GemsState`. Persistir o record DENTRO do state (antes do ledger), garantindo que a idempotência sobreviva a crash.

**Estrutura:**
```java
public static class IdempotencyPersistedRecord {
    String transactionId;
    String operationType;
    UUID playerUuid;
    long amount;
    UUID reservationId;
    String resultStatus;
    long createdAt;
    String requestFingerprint;
}
```

**Fluxo modificado:**
1. Mutação → cloneState → aplica mutação → **adiciona idempotencyRecord ao nextState** → saveState → swap → appendTransaction

### P0-04 — Pending Audit Entries (HIGH)

**Problema:** `pendingAuditEntries` existe em `GemsState` mas nunca é populado nas operações. Apenas verificado em `recover()`.

**Solução:** Em cada mutação financeira, antes de persistir, criar um `PendingAuditEntry`. Após ledger append bem-sucedido, criar novo state removendo a pendência.

### P0-07 — Cenário #8 de Concorrência

Adicionar teste de `cleanup` concorrente com `capture`.

### P0-09 — Testes de Comando

Criar testes que exercitam a lógica de comando via API de serviço, simulando os comportamentos de:
- give, take, set, reset
- reservations, reservation inspect, reservation release
- verify, repair, reload
- amount decimal, negativo, zero
- overflow, take acima do available, set abaixo do held
- reset com reservation ACTIVE
- alias /gemas
- mensagens sem stacktrace

### P0-10 — Regras de Shutdown

Validar:
- Mutação bloqueada durante shutdown
- pendingAuditEntries sobrevivem a shutdown
- Recovery reconcilia pendências
- Executor finalizado corretamente
