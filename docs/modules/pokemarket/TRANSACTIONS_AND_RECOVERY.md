# Transações e recuperação

As migrations de V016 a V021 criam toda a infraestrutura do mercado:

| Migration | Tabelas |
|---|---|
| V016 | `bbe_pokemarket_listings`, `bbe_pokemarket_claims`, `bbe_pokemarket_transactions`, `bbe_pokemarket_audit_log` |
| V017 | `bbe_pokemarket_escrow` (um Pokémon ativo por UUID) |
| V018 | `bbe_economy_operations` (journal econômico) |
| V019 | `bbe_pokemarket_purchase_operations` (compras duráveis) |
| V020 | `bbe_economy_accounts` (contas BIGINT), `bbe_economy_data_migrations` |
| V021 | `bbe_pokemarket_trade_operations` (trocas Pokémon-por-Pokémon) |
| V029 | `bbe_pokemarket_escrow` sem `UNIQUE(listing_id)` (trade escrowa listado + oferecido sob o mesmo anúncio) |

Em `backend: DATABASE`, `bbe_economy_accounts.balance_minor` é a fonte de verdade; `bbe_economy_operations` e a alteração da conta usam a mesma transação JDBC. O JSON legado só é lido por `/bbe economy migrate-json --dry-run` ou pelo `--execute --confirm`, nunca como fallback.

## Modelo econômico

O modelo canônico é `BIGINT` em unidades mínimas, escala fixa 2 e `HALF_UP`. A escala é configuração de instalação e não deve ser alterada depois da importação sem migration própria. Débitos usam `UPDATE ... balance_minor >= ?`; créditos usam limite atômico de saldo. A chave única de idempotência retorna o receipt já persistido.

## Compra

Compra nova: reserva, débito, claims, venda, purchase operation, auditoria e liberação do escrow são uma transação JDBC única. Estados: `CREATED` → `LISTING_RESERVED` → `DEBIT_PENDING` → `DEBIT_CONFIRMED` → `CLAIMS_PENDING` → `CLAIMS_CREATED` → `COMPLETED`. Recovery cria claims ausentes usando chaves únicas, conclui a venda e libera o escrow; não debita novamente. `PREPARING` vai para `RECOVERY_REQUIRED` porque exige inspeção do storage no server thread.

Claim financeiro faz crédito e `CLAIMED` na mesma transação.

## Troca

Troca: operação em transação JDBC única. Estados: `CREATED` → `LISTING_RESERVED` → `OFFER_IN_ESCROW` → `CLAIMS_CREATED` → `COMPLETED`. Ambos os Pokémon são serializados, removidos do storage original e colocados em claims separadas. Recovery conclui somente a partir de `OFFER_IN_ESCROW`; uma queda antes da remoção é ambígua e exige reconciliação manual. A conclusão, inclusive via recovery, libera os dois escrows.

## Reembolso

`/pokemarket admin refund <id> <motivo>` reivindica a operação e verifica a listagem `RESERVED` antes de creditar o comprador. Crédito (chave `pokemarket:refund:<operationId>`), claim de devolução ao vendedor, `RESERVED → CANCELLED` e liberação do escrow comitam juntos. Claim prévio do comprador ou listagem ausente/incompatível bloqueiam o reembolso em `RECONCILIATION_REQUIRED`, sem movimentar dinheiro. Apenas administrativo; não há reembolso automático diante de estado ambíguo.

## Configuração

`pokemarket.json` (defaults): `saleTaxPercentage: 5.0` (a taxa precisa deixar valor positivo ao vendedor), `price.min: 0.01` / `price.max: 1000000.00` (ambos positivos e mínimo não pode exceder máximo) e `recovery.reservedTimeoutMinutes: 5` (idade mínima para recovery liberar um `RESERVED` órfão).

## Recovery

Executado na inicialização do módulo:

1. `PokeMarketRecoveryService.recover()` — quarantina `PREPARING` → `RECOVERY_REQUIRED`
2. `PokeMarketPurchaseService.recover()` — busca compras incompletas, tenta concluir
3. `PokeMarketTradeService.recover()` — busca trocas incompletas, tenta concluir

Nenhum Pokémon é recriado sem evidência. Ambiguidades vão para `RECONCILIATION_REQUIRED`.

Cancelamento administrativo aceita `ACTIVE`; um `RESERVED` só é cancelável se não houver compra ou troca incompleta ligada à listagem. Operações pendentes devem usar refund ou reconciliação.

## Importador JSON

O importador cria backup com SHA-256, valida UUID/valor/escala/overflow, grava relatório em `bbe_economy_data_migrations` e renomeia o legado somente após reconciliação. Conflitos entram em `RECONCILIATION_REQUIRED`.

## Export

`/bbe economy export` lê todas as contas e escreve `economy-export-<data>-<checksum>.json` com timestamp e SHA-256. Não sobrescreve exports anteriores.
