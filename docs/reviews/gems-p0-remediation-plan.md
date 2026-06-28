# P0 Remediation Plan — Sistema Gems

## Baseline

| Item | Valor |
|---|---|
| SHA inicial | `ac4d4a829b73aaf97d78fd7b93bd51221fdf5092` |
| Branch | `master` |
| Build | `BUILD SUCCESSFUL` (6s) |
| Árvore | Limpa (apenas audit.md modificado na sessão anterior) |
| Loader | Java 21 LTS, Fabric + NeoForge |

## Achados da auditoria anterior

| # | Severidade | Descrição | Arquivo | Status inicial |
|---|---|---|---|---|
| A4 | HIGH | Crash injection coverage insuficiente: 3/8 failpoints testados | `GemCrashInjectionTest.java` | Aberto |
| A5 | HIGH | Failpoints incompletos: 8/12 definidos | `GemsPersistenceFailpoint.java` | Aberto |
| A6 | MEDIUM | `GemReleaseRequest` sem `idempotencyKey` | `GemReleaseRequest.java` | Aberto |
| A7 | MEDIUM | Concorrência incompleta: 4/12 cenários testados | `GemReservationConcurrencyTest.java` | Aberto |
| A8 | LOW | `GemRenewRequest` sem `idempotencyKey` | `GemRenewRequest.java` | Aberto |
| A9 | LOW | `executeAdminReset` usa `fallbackStarting = 0` hardcoded | `GemsCommand.java:479` | Aberto |
| A10 | LOW | Nenhum teste de execução real de comando | `GemCommandAuthorizationTest.java` | Aberto |
| A11 | LOW | Documentação desatualizada (corrigido na auditoria) | `docs/reviews/gems-independent-audit.md` | Corrigido |

## Arquivos afetados

### Código fonte
- `GemsPersistenceFailpoint.java` — adicionar 4 failpoints
- `GemsManager.java` — adicionar failpoint checks em todas as operações; adicionar idempotência em release/renew; corrigir pendingAuditEntries
- `GemsPersistence.java` — salvar/carregar pendingAuditEntries; loadIdempotencyFromLedger
- `GemsState.java` — adicionar `lastAppliedTransactionId`, `pendingAuditEntries`
- `GemReleaseRequest.java` — adicionar `idempotencyKey`, `source`, `purpose`, `externalReference`, `metadata`
- `GemRenewRequest.java` — adicionar `idempotencyKey`, `source`, `purpose`, `externalReference`, `requestedLease`, `metadata`
- `GemsCommand.java` — corrigir `executeAdminReset` para usar `startingBalance`

### Testes
- `GemCrashInjectionTest.java` — adicionar 5 novos cenários de failpoint
- `GemReservationConcurrencyTest.java` — adicionar 8 novos cenários
- `GemReservationIdempotencyTest.java` — adicionar testes de release/renew idempotentes
- `GemCommandAuthorizationTest.java` — expandir para comandos reais
- Novo: `GemCommandExecutionTest.java` — testes de execução real de comandos

### Documentação
- `docs/reviews/gems-p0-remediation-report.md` — relatório final
- `docs/Wiki/GemsSystem.md`, `GemsAPI.md`, `GemsReservations.md`, `GemsTransactions.md`, `GemsTroubleshooting.md`
- `docs/integrations/bigbangregions-gems-api.md`
- `CHANGELOG.md`

## Critérios de fechamento

- [ ] 12 failpoints implementados e testados
- [ ] Cada failpoint tem teste de restart/recovery
- [ ] Sem perda/duplicação de Gems após failpoint
- [ ] State/ledger com reconciliação comprovada
- [ ] Release com idempotencyKey
- [ ] Renew com idempotencyKey
- [ ] 12 cenários concorrentes executados
- [ ] Invariantes financeiros OK em todos cenários
- [ ] Reset usa startingBalance configurado
- [ ] Comandos com testes de execução reais
- [ ] Coins/Vault sem regressão
- [ ] `./gradlew clean test build` passa
- [ ] Documentação reflete resultados reais
- [ ] Nenhum achado HIGH aberto
