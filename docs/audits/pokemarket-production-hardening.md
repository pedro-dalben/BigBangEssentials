# Auditoria de homologação — PokéMarket

## Estado confirmado

- Branch criada: `feat/pokemarket-production-hardening`.
- Workspace recebido sujo e preservado; nenhum arquivo não rastreado foi removido.
- Migrations encontradas V001–V022; V022 é a migração mais alta registrada.
- Antes desta etapa não existiam tasks `mysqlIntegrationTest`, `pokeMarketConcurrencyTest`, `pokeMarketFaultInjectionTest` ou runner Cobblemon.

## Correções

- Fault injection: checkpoints de preparação/remoção/ativação e troca adicionados, sem duplicar nomes.
- Notificações persistentes: tabela V022, estados `UNREAD/DELIVERED/READ`, paginação, leitura individual, leitura em massa e marcação no login.
- Jobs: recompensa usa receipt idempotente no backend database quando disponível.
- Rankup: cobrança e compensação usam chaves derivadas da transação no backend database quando disponível.
- Crates: compra/reembolso usam chaves derivadas da abertura no backend database quando disponível.
- Menu principal recebeu entradas de troca, histórico, notificações e ajuda; os fluxos party/PC completos ainda exigem UI runtime.

## Revisão de segurança (2026-07-31)

Correções aplicadas após auditoria de integridade patrimonial:

- **Refund atômico** (`PokeMarketPurchaseService.refund`): uma transição exclusiva para `REFUND_PENDING` impede concorrência; listagem e claim do comprador são verificadas antes do crédito. Crédito + claim do vendedor + `RESERVED→CANCELLED` + liberação do escrow comitam juntos.
- **Trade escrow**: `INSERT OR REPLACE` virou `INSERT` (conflito real); rollback remove o escrow somente da própria listagem; conclusão normal e recovery limpam ambos os escrows. `LISTING_RESERVED` após crash vai para reconciliação, pois a remoção física ainda é ambígua. V029 remove `UNIQUE(listing_id)`.
- **Purchase escrow**: compra concluída e recovery liberam o escrow da listagem antes de marcar a operação como concluída.
- **Recovery**: `recoverStaleReserved` respeita `recovery.reservedTimeoutMinutes` (antes liberava qualquer `RESERVED`).
- **Admin cancel**: funciona em `ACTIVE`; `RESERVED` só é permitido sem compra/troca incompleta associada.
- **NBT**: `deserialize` limitado a 1 MB / profundidade 512 (anti-OOM).
- **Preço/taxa**: limites `price.min/max` e `saleTaxPercentage` configuráveis em `pokemarket.json`; configuração inválida ou taxa que zere o repasse é rejeitada antes da compra.

Testes novos: `PokeMarketRefundTest`, `PokeMarketRecoveryTimeoutTest`, `PokeMarketEscrowTest`, `PokeMarketPricingBoundsTest` (+ asserções de state machine em `PokeMarketDomainTest`). Runtime Cobblemon permanece `BLOCKED` (sem GameTest reproduzível).

## Limitações honestas

- ChestShop agora compensa falhas de crédito com rollback de itens e dinheiro; a saga durável após crash permanece `PARTIAL`. AdminShop usa operações econômicas idempotentes.
- FULL health agora executa verificações assíncronas, agregadas e leitura de checksums de trades em lotes de 100; o caminho SQLite é coberto por `PokeMarketHealthServiceTest`.
- Recovery de compra e troca foi testado com serviços/repositórios reconstruídos após reabrir o SQLite e com replay idempotente (`PokeMarketRecoveryReconstructionTest`).
- Não há GameTest/servidor Cobblemon reproduzível no workspace; runtime Cobblemon é `BLOCKED` até staging real.
- MySQL depende de Docker ou variáveis `BBE_TEST_MYSQL_*`; sem elas a suíte é automaticamente assumida como não executável.
- Fault test automatiza a injeção do conjunto de checkpoints; cenários end-to-end com `ServerPlayer` precisam staging/runtime.
