# Auditoria profunda de economia e lojas

Data da execução: 2026-07-22  
Escopo: money, gems, `/pay`, `/shop`, ChestShop, `/sell`, crates, rankup, jobs, PokéMarket e Vault.  
Regra desta etapa: somente caracterização, relatório e testes; nenhum arquivo de produção foi alterado nesta etapa. O worktree já estava sujo e suas alterações foram preservadas.

## Como reproduzir

Na raiz do repositório:

```text
git rev-parse HEAD
git status --short
rg -n --glob '*.java' '(addBalance|subtractBalance|setBalance|deposit\(|withdraw\(|credit\(|debit\(|transfer\(|reserve\(|capture\(|release\()' common/src/main/java
./gradlew :common:test --tests 'com.pedrodalben.bigbangessentials.audit.EconomyAuditCharacterizationTest'
./gradlew :common:test --tests 'com.pedrodalben.bigbangessentials.economy.DatabaseEconomyServiceTest' --tests 'com.pedrodalben.bigbangessentials.adminshop.AdminShopAuditStoreTest' --tests 'com.pedrodalben.bigbangessentials.crates.integration.CrateEconomyIntegrationTest' --tests 'com.pedrodalben.bigbangessentials.rankup.RankupEconomyFacadeTest' --tests 'com.pedrodalben.bigbangessentials.shop.model.ShopDataTest'
```

O primeiro teste é uma caracterização deliberadamente pequena do código atual: fixa a evidência do debounce do `/shop` e das fronteiras financeiras encontradas, sem criar uma API de teste ou alterar produção.

Resultado desta execução (2026-07-22): o teste de caracterização passou em 3 s; a bateria focada de money, gems, AdminShop, ChestShop, crates, rankup e PokéMarket passou em 10,46 s. O build terminou com `BUILD SUCCESSFUL`.

## Portas de economia

| Porta | Implementação atual | Persistência | Observação de auditoria |
|---|---|---|---|
| `EconomyManager` | API interna usada pela maior parte dos módulos | `balances.json` no modo `JSON`; `bbe_economy_accounts` no modo `DATABASE` | `mutateLocal` é sincronizado; chaves JSON ficam apenas em memória |
| `EconomyAPI` | Fachada pública que delega ao `EconomyManager` | Igual à porta anterior | `/pay` chama `EconomyManager.transfer` |
| `BigBangEssentialsAPI` | Serviço configurado em `BigBangEssentialsManager` | `DatabaseEconomyService` em `DATABASE`; wrapper `EconomyServiceImpl` em `JSON` | Integrações externas podem substituir o serviço com `setEconomyService` |
| `DatabaseEconomyService` | JDBC idempotente | `bbe_economy_accounts` + `bbe_economy_operations` | Cada operação simples usa uma transação JDBC; chaves não têm fingerprint de argumentos |
| `GemsManager` | Estado próprio com `ReentrantReadWriteLock` | `gems_state.json` + `gems_transactions.jsonl` | Estado é gravado antes do ledger; chaves têm fingerprint e conflito explícito |
| `Vault` | `BigBangEssentialsEconomy` → `EconomyManager` | Igual ao manager | Contrato externo continua usando `double` e chaves aleatórias |

Fontes principais: `EconomyManager`, `EconomyAPI`, `BigBangEssentialsManager`, `DatabaseEconomyService`, `GemsManager` e `BigBangEssentialsEconomy`.

## Matriz de fluxos

| Fluxo | API/mutação | Chave | Ordem saldo/itens | Transação e bloqueio |
|---|---|---|---|---|
| `/pay` | `PayCommand` → `EconomyAPI.payPlayer` → `EconomyManager.transfer` | `pay:<sender>:<receiver>:<UUID aleatório>`; DB deriva `:debit`/`:credit` | DB atualiza débito e crédito na mesma transação; JSON atualiza os dois caches antes dos logs | `synchronized` no caminho local; JDBC transaction no DB; cooldown do comando é separado |
| AdminShop `/shop` | `AdminShopTransactionService.execute` | `adminshop:buy|sell:<tx>`; gems também reserva/captura | BUY: saldo/gems → item/comando → stock/limit/demand; SELL: item → crédito → estado | método inteiro `synchronized`; `startAudit`, save e log usam `.join()`; itens não participam da transação SQL |
| ChestShop compra | `ShopTransaction.executeBuy` | `chestshop:buy:debit|credit|refund:<tx>` | saldo comprador → remove chest → entrega item → crédito dono | cada operação monetária é separada; inventário/chest ficam fora; não há lock ou saga durável |
| ChestShop venda | `ShopTransaction.executeSell` | `chestshop:sell:debit|credit|rollback:<tx>` | remove vendedor → débito dono → adiciona chest → crédito vendedor | mesma separação; rollback é best effort |
| `/sell` | `SellCommand` → `EconomyManager.addBalance` | nenhum key externo; manager gera UUID | remove item primeiro; só depois tenta crédito | sem transação, receipt, auditoria ou rollback |
| Crates | `CrateOpeningService` → `CrateEconomyIntegration` | `crate:purchase:<opening>` e `crate:refund:<audit>`; audit key impede replay | key → money → cooldown/contagem → reward; rollback em ordem reversa | lock por jogador; audit/keys/DB e money não compartilham transação |
| Rankup | `RankupPromotionService` + `GemsManager` | `rankup:charge:<tx>`, gem usa key da tx, refund usa `rankup:refund:<tx>` | dinheiro → gems → LuckPerms → progresso/histórico/ações; compensação reversa | fila por jogador e transação de rankup são separadas do money/gems |
| Jobs | `JobActionProcessor` → `JobRewardApplier` | DB: `jobs:reward:<actionId>`; JSON: `EconomyAPI.deposit` sem key | crédito → daily earnings → XP/side effects | receipt da ação é separado do saldo; jobs elegíveis compartilham a mesma key |
| PokéMarket compra | `PokeMarketPurchaseService` | `pokemarket:purchase:debit:<operation>` e claims derivados | reserva listing → débito → claims → SOLD → audit | caminho DB novo coloca listing, money, claims e audit na mesma transação JDBC |
| PokéMarket claim money | `PokeMarketClaimService` | `pokemarket:claim-money:<claim>` | crédito e `CLAIMED` na mesma transação DB; fallback usa saga idempotente | `markProcessing` precede a entrega; retry é por claim |
| PokéMarket trade | `PokeMarketTradeService` | operação/claims por UUID | escrow Pokémon → remove → claims dos dois lados → TRADED | não há money; integridade é de escrow/claims |
| Vault | `BigBangEssentialsEconomy.withdrawPlayer/depositPlayer` | UUID aleatório gerado pelo manager | apenas money | `double` na borda; não participa das sagas de itens |

## `/shop`: caracterização reproduzível

O bloqueio não é uma transação pendente. Ele acontece antes de criar a transação:

```java
String clickKey = player.getUUID() + ":" + productId + ":" + operation;
if (now - recentClicks.getOrDefault(clickKey, 0L) < 400)
    return fail("§7Aguarde a conclusão da transação.");
```

O mesmo `player + produto + operação` só pode passar uma vez a cada 400 ms. Cada chamada aceita depois disso recebe um UUID novo e uma compra independente. O `execute` inteiro é `synchronized`, então jogadores e produtos diferentes também esperam pelo mesmo monitor. Dentro desse monitor há I/O síncrono: auditoria SQL, reserva/captura de gems, operações JDBC, escrita de estado e log legado.

Conclusão de aceitação futura: remover o cooldown artificial como mecanismo de proteção e manter a atomicidade/idempotência na operação real; cada clique válido deve resultar em uma compra independente. Isso é recomendação, não correção aplicada nesta etapa.

## Falhas confirmadas

### P1 — `/sell` pode remover itens sem pagar

- Fluxo: `/sell hand`, `/sell <item>` e `/sell inventory|all`.
- Reprodução: saldo no máximo, backend indisponível ou crédito recusado; execute a venda.
- Evidência: `SellCommand` remove o item em `doSell` e chama `addBalance` depois sem verificar o retorno. A venda em lote esvazia os slots antes de tentar o crédito e também ignora o retorno.
- Impacto: perda de itens; não há duplicação necessária para reproduzir.
- Correção recomendada: creditar com receipt antes de confirmar a remoção, ou guardar uma saga/rollback que valide o resultado do crédito.

### P1 — Jobs pode creditar uma vez e registrar earnings várias vezes

- Fluxo: uma ação que resolve para mais de um job elegível no backend `DATABASE`.
- Reprodução: os dois jobs passam pelo loop de `JobActionProcessor` e `JobRewardApplier` com a mesma `actionId`.
- Evidência: ambos usam `jobs:reward:<actionId>`. `DatabaseEconomyService.existing` devolve o receipt anterior para qualquer quantidade/tipo compatível com a chave, enquanto `JobRewardApplier` incrementa o daily earnings para cada aplicação.
- Impacto: dinheiro abaixo do valor esperado e earnings/receipt divergentes; se o segundo valor diferir, ainda assim o primeiro receipt é tratado como sucesso.
- Correção recomendada: chavear por `actionId + jobId` quando o contrato permitir múltiplos jobs, ou fazer a operação multi-job explicitamente atômica.

### P1 — Jobs em JSON não têm idempotência persistente do crédito

- Fluxo: crash depois do crédito e antes do receipt de `JobActionProcessor`.
- Evidência: o branch JSON chama `EconomyAPI.deposit` sem chave; o registro da ação é gravado depois.
- Impacto: replay após reinício pode duplicar a recompensa.
- Correção recomendada: journal persistente por `actionId` também no backend JSON, ou exigir backend transacional para esse fluxo.

### P1 — Refund de Crate é ignorado

- Fluxo: crate paga, falha no reward/inventário e entra em rollback.
- Evidência: `CrateOpeningService.rollback` chama `economyIntegration.deposit(...)`, mas não testa o `boolean` retornado; mesmo assim pode salvar `ROLLED_BACK`.
- Impacto: saldo pode continuar debitado enquanto o audit afirma rollback limpo.
- Correção recomendada: marcar `COMPENSATION_FAILED` quando o crédito retornar falso e deixar a operação em reconciliação.

### P1 — Falha de gems pode ser marcada como compensada sem confirmar refund de money

- Fluxo: Rankup com money debitado, gems recusadas e crédito de refund recusado/falhando.
- Evidência: `chargeGems` chama `EconomyManager.credit`, ignora o resultado e salva `tx.withCompensated(true)`.
- Impacto: o débito pode permanecer sem money e a recuperação posterior pode ser pulada.
- Correção recomendada: só marcar compensado após receipt `COMPLETED`; caso contrário, persistir `RECOVERY_REQUIRED`.

### P1 — AdminShop command product pode cobrar sem confirmar entrega

- Fluxo: produto de `/shop` configurado com `command`.
- Evidência: `performPrefixedCommand` é chamado e seu retorno não é verificado; `commandAttempted` não distingue comando executado com sucesso de comando recusado.
- Impacto: money/gems capturados sem recompensa; rollback também considera comando não compensável.
- Correção recomendada: contrato de comando com resultado verificável ou classificar o produto como não transacional e não cobrar antes de uma confirmação.

### P1/P2 — ChestShop é uma saga sem estado durável

- Fluxo: compra/venda com falha de item, limite de saldo, chest alterado ou crash entre etapas.
- Evidência: money e itens são aplicados em chamadas separadas; os rollbacks de item/money não têm audit de transação nem resultado de reconciliação. `addItems` não retorna quanto realmente inseriu.
- Impacto: perda ou duplicação possível em falhas/crash; concorrência no mesmo chest pode invalidar os pre-checks.
- Correção recomendada: lock por chest/conta, receipt persistente e estado de saga com reconciliação; validar a quantidade efetivamente movida.

### P2 — O débito/crédito JDBC não detecta colisão de fingerprint

- Fluxo: duas operações reutilizam a mesma idempotency key com jogador, valor ou operação diferentes.
- Evidência: `DatabaseEconomyService.mutate` retorna `existing(c, key)` sem comparar argumentos. Gems já possui comparação de fingerprint; money não.
- Impacto: chamada diferente pode receber sucesso do receipt antigo, ocultando underpayment, overpayment ou erro de integração.
- Correção recomendada: persistir fingerprint/argumentos e retornar conflito explícito para chave reutilizada com payload diferente.

### P2 — `/shop` recusa compras válidas por cooldown artificial

- Fluxo: dois cliques do mesmo jogador no mesmo produto/operação em menos de 400 ms.
- Evidência: `EconomyAuditCharacterizationTest.adminShopUsesTheKnownGlobalFourHundredMillisecondGate` fixa o literal, a mensagem e o monitor global.
- Impacto: compra válida perdida; não é uma transação pendente nem uma proteção de saldo.
- Correção recomendada: trocar debounce por controle de concorrência/idempotência transacional que não descarte cliques válidos.

## Riscos arquiteturais

- `EconomyAPI` e `BigBangEssentialsAPI` continuam sendo duas portas. No funcionamento padrão ambos apontam para o mesmo backend configurado, mas integrações que usam `setEconomyService` podem divergir do `EconomyManager`, que é usado diretamente por `/pay`, Crates e Rankup.
- A escolha do backend é capturada na inicialização dos singletons. Alterar configuração depois que uma porta foi inicializada não reconfigura o objeto já criado.
- Money em JSON tem `localOperations` somente em memória; a persistência de saldo é assíncrona. Gems tem journal/pending audit mais forte, mas continua fora das transações de money e de itens.
- `Vault`, jobs e alguns contratos legados mantêm `double`. A persistência nova usa `BigDecimal`/minor units, mas arredondamento e limites ainda podem divergir na fronteira.
- `/pay` é atomicamente aplicado, mas a key usa UUID aleatório por chamada. Uma repetição após resposta perdida é uma nova transferência, não um replay idempotente. O cooldown do comando não é uma garantia durável.

## Desempenho e bloqueios

Não foi usado limite de milissegundos dependente de hardware. O ponto bloqueante caracterizado é estrutural:

1. `AdminShopTransactionService.execute` segura um monitor global.
2. Dentro dele, chamadas JDBC fazem `.join()`.
3. Gems grava estado e ledger em I/O síncrono.
4. `AdminShopManager.saveState` regrava JSON e reconstrói tabelas de estado em transação.

Assim, a latência de um jogador pode bloquear qualquer outro jogador no AdminShop. O próximo passo deve medir p50/p95/p99 em staging com o mesmo backend e carga, sem transformar um número de uma máquina em contrato.

## Cobertura executada e limites

| Área | Evidência existente no worktree |
|---|---|
| Money concorrente, saldo insuficiente, replay e limite do destinatário | `DatabaseEconomyServiceTest` |
| Gems: máximo, reserva, captura, release, concorrência, fingerprint e recovery | `GemBalanceServiceTest`, `GemReservationConcurrencyTest`, `GemReservationIdempotencyTest`, `GemReservationRecoveryTest` |
| AdminShop: auditoria, histórico sem audit e estado SQL | `AdminShopAuditStoreTest` |
| ChestShop: classificação de legado e bloqueio de entrada | `ShopDataTest`; caminho ServerPlayer/chest completo ainda exige runtime |
| Crates: key, concorrência, limite, idempotência e rollback de estado | `CrateTransactionalTest`, `CrateEconomyIntegrationTest` |
| Rankup: compensação de money por key | `RankupEconomyFacadeTest`; pipeline completo exige servidor/DB/config |
| Jobs: deduplicação da ação e cálculo/limites | `JobActionPipelineTest`, `JobsSystemTest`, `JobsXpRefactorTest`; crédito multi-job e falha de crédito ainda são lacunas |
| PokéMarket: concorrência, fault injection, recovery e claims | `PokeMarketConcurrencyTest`, `PokeMarketFaultInjectionTest`, `PokeMarketRecoveryReconstructionTest` |
| `/pay` e `/sell` end-to-end | não há teste reproduzível com `CommandSourceStack`/inventário neste workspace; ficam como lacunas P1/P2 acima |

Os testes com Cobblemon real e integração MySQL continuam condicionais ao runtime externo, conforme a documentação existente. Nenhuma correção de produção foi aplicada a partir dos achados.

## Priorização

1. Corrigir `/sell`, refund de Crates e estado de compensação de Rankup: risco direto de perda.
2. Corrigir composição de keys de Jobs e fingerprint do journal money: risco de divergência/underpayment.
3. Definir saga durável para ChestShop e resultado verificável para command products do AdminShop.
4. Remover o debounce de 400 ms do `/shop` e medir o caminho fora do monitor global.
5. Unificar as portas de economia e decidir a política de retry de `/pay`.
