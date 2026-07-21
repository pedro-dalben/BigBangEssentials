# PokéMarket — Staging/UAT

Preencher uma linha por execução. Um cenário só é `PASS` com evidência anexada; compilação não substitui runtime.

| cenário | pré-condições | passos | resultado esperado | resultado obtido | PASS/FAIL | evidências | observações |
|---|---|---|---|---|---|---|---|
| Economia: migration JSON/dry-run/import | fixture aprovada, backup | executar dry-run e import | totais iguais, sem duplicação |  |  |  |  |
| Economia: débito/crédito/replay | conta com saldo | repetir mesma chave | um receipt, um efeito |  |  |  |  |
| Economia: export/reconcile/restart | banco saudável | exportar, reiniciar, reconciliar | nenhuma divergência |  |  |  |  |
| Venda party | servidor Cobblemon real | anunciar, reiniciar, comprar, claim | UUID e atributos preservados |  |  |  |  |
| Venda PC | PC com Pokémon elegível | repetir fluxo party no PC | mesmo resultado |  |  |  |  |
| Cancelamento | anúncio ativo | cancelar e retirar claim | Pokémon e taxa corretos |  |  |  |  |
| Expiração | anúncio curto | desligar, aguardar, ligar, processar | claim de devolução |  |  |  |  |
| Compra concorrente | 20 jogadores, 1 anúncio | comprar simultaneamente | 1 vencedor, 19 rejeições |  |  |  |  |
| Troca compatível/incompatível | dois Pokémon reais | validar, aceitar, retirar claims | 2 claims, UUIDs únicos |  |  |  |  |
| Troca concorrente | 20 ofertantes | aceitar simultaneamente | 1 trade concluída |  |  |  |  |
| Storage cheio | party/PC cheios | tentar claim | claim permanece disponível |  |  |  |  |
| Crash checkpoints | fault injector habilitado | interromper em cada checkpoint, reconstruir serviços, recovery duas vezes | invariantes preservadas |  |  |  |  |
| MySQL | Docker ou banco externo | `./gradlew mysqlIntegrationTest` | migration V001–V022 e fluxo passam |  |  |  |  |
| Cobblemon runtime | JAR compatível e servidor staging | round-trip party/PC e escrow | todos os atributos preservados |  |  |  |  |

## Critério de liberação

Economia, venda, troca, escrow, claims, recovery, concorrência, MySQL e Cobblemon runtime precisam ser `PASS`. `runPokeMarketIntegrationTest` permanece bloqueada enquanto não houver GameTest/servidor reproduzível configurado.

## Evidência automatizada local (2026-07-21)

| verificação | resultado | evidência |
|---|---|---|
| `./gradlew clean build --offline --no-daemon` | PASS | build 329 |
| `./gradlew verifyNoBundledCobblemon --offline --no-daemon` | PASS | build 330 |
| `./gradlew pokeMarketConcurrencyTest pokeMarketFaultInjectionTest runWithoutCobblemonTest --offline --no-daemon` | PASS | build 331 |
| `./gradlew :common:test --tests '*PokeMarketRecoveryReconstructionTest' --offline --no-daemon` | PASS | build 351; recovery após reabrir SQLite e replay idempotente |
| `./gradlew :common:test pokeMarketConcurrencyTest pokeMarketFaultInjectionTest runWithoutCobblemonTest --offline --no-daemon` | PASS | build 352 |
| `./gradlew clean build --offline --no-daemon` | PASS | build 353 |
| `./gradlew verifyNoBundledCobblemon --offline --no-daemon` | PASS | build 354 |
| `./gradlew runPokeMarketIntegrationTest --offline --no-daemon` | BLOCKED | build 355; sem GameTest/servidor reproduzível |
| `./gradlew mysqlIntegrationTest --offline --no-daemon` | SKIPPED | build 356; sem Docker e sem `BBE_TEST_MYSQL_*` |

Os cenários que exigem servidor Cobblemon real ou MySQL continuam sem execução e não são considerados PASS.
