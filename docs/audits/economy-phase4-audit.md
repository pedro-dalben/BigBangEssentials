# Auditoria da economia — fase 4

| componente | operação | API atual | monetário | fonte em DATABASE | idempotência | JDBC única | migração |
|---|---|---|---|---|---|---|---|
| `EconomyManager` | saldo/deposit/withdraw/set | facade legada → `DatabaseEconomyService` | `BigDecimal` na borda, `BIGINT` persistido | `bbe_economy_accounts` | chave gerada pela facade; API idempotente aceita chave externa | sim para cada operação | sim |
| `EconomyServiceImpl` | API compatível/Vault | wrapper; journal legado só no modo JSON | `double` compatível na API | banco quando `backend=DATABASE` | sim no backend DB | sim | sim |
| `/eco`, `/pay`, adminshop, chest shop, sell | cobrança/crédito | `EconomyManager` | entradas antigas `double`, normalizadas em `Money` | banco | chave automática para chamadas sem chave | operação simples sim; fluxos item+dinheiro ainda saga | sim |
| jobs/rankup/crates | rewards/cobranças | integrações existentes | alguns campos de configuração ainda `double` | via manager/API | depende do módulo; não timestamp-only no novo caminho | operação simples sim | parcial: callers passam pela facade |
| Vault | depósito/saque/leitura | `BigBangEssentialsEconomy` | contrato Vault usa `double` | banco | UUID de operação por chamada | sim | sim |
| PokéMarket purchase | venda | `PokeMarketPurchaseService` | `BigDecimal` escala 2 | banco | purchase/debit/claims únicas | sim no caminho novo | sim |
| PokéMarket money claim | crédito/claim | `PokeMarketClaimService` | `BigDecimal` | banco | claim id | sim | sim |

## Acessos diretos encontrados

O acesso de escrita legado estava concentrado em `EconomyManager` (`balances.json`) e na migração automática de `EconomyServiceImpl`; ambos ficam fora do caminho quando `backend=DATABASE`. Leituras diretas por comandos, tablist, placeholders e dashboard passam pelo `EconomyManager`, que agora consulta a conta JDBC. O JSON permanece aceito apenas no modo explícito `JSON` ou no serviço de migração.

Ainda existem tipos `double` no contrato de compatibilidade Vault, eventos antigos, parsing de comandos e alguns modelos de jobs/crates. Eles não são usados para persistência; novas operações financeiras convertem com escala/rounding centralizados. A remoção total exigiria quebrar APIs públicas existentes.
