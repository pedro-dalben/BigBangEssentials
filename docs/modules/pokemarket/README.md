# PokéMarket

Módulo completo de mercado virtual de Pokémon com suporte a venda por dinheiro e troca Pokémon-por-Pokémon.

## Requisitos

- Cobblemon 1.7.3+1.21.1 (Fabric ou NeoForge)
- Banco de dados (`backend: DATABASE`)
- Módulo economy ativo

## Funcionalidades

### Central visual

`/pokemarket`, `/gts` e `/pm` abrem a central visual do módulo. A central
oferece exploração paginada, filtros por tipo, espécie, shiny, nível, IVs,
faixa de preço e ordenação, detalhes do anúncio, compra com confirmação,
party/PC para publicação, construtor guiado de trocas, claims, notificações e
registros pessoais.

O valor de publicação e os limites de preço podem ser digitados no chat
temporariamente. A entrada é privada, expira automaticamente, aceita `cancel`
e nunca movimenta Pokémon ou dinheiro sem a confirmação no menu.

Os menus padrão são copiados para `config/bigbangessentials/menus/` somente
quando ainda não existem e podem ser customizados e recarregados com
`/bbmenu reload`. O catálogo usa os anúncios ativos para montar o seletor de
espécies; a consulta filtrada permanece no banco e não carrega todos os
Pokémon para o servidor.

### Venda por dinheiro (`/pokemarket sell party|pc`)
- Anúncio pela party ou PC com preço e duração configurável
- Escrow do Pokémon durante o anúncio
- Compra atômica via transação JDBC (reserva, débito, claims)
- Taxa de venda (5%) calculada automaticamente
- Claims de Pokémon para o comprador e dinheiro para o vendedor
- Histórico de transações

### Troca Pokémon-por-Pokémon (`/pokemarket trade party|pc <json>`)
- Anúncio especificando requisitos em JSON (espécie, shiny, level, IVs, forma)
- Validação do Pokémon oferecido contra requisitos
- Escrow de ambos os Pokémon
- Claims para ambos os jogadores
- Operação persistente com recovery

### Retirada de claims (`/pokemarket claim <id|all|money|pokemon>`)
- Claims de Pokémon entregues ao storage do jogador
- Claims de dinheiro creditados via DatabaseEconomyService
- Idempotência total
- Com `backend: DATABASE`, indisponibilidade do banco bloqueia novas compras,
  claims monetários e recovery; não há fallback JSON.

### Notificações (`/pokemarket notifications`)
- Consulta de claims pendentes, compras e trocas recentes

### Administração (`/pokemarket admin`)
- Health check QUICK e FULL
- Estatísticas, listagens, operações, trocas
- Inspeção de anúncio
- Cancelamento administrativo

## Comandos

`/pokemarket`, `/gts` e `/pm` abrem a central visual. Os subcomandos antigos
continuam registrados exclusivamente para compatibilidade com menus já
instalados; os novos fluxos de jogador são conduzidos pela GUI.

Staff ainda pode usar `/pokemarket admin ...` como compatibilidade operacional;
o acesso recomendado é o botão **Painel Staff** dentro da central.

## Migrations

| Versão | Descrição |
|---|---|
| V016 | Tabelas de listagens, claims, transações e auditoria |
| V017 | Escrow (um Pokémon ativo por UUID) |
| V018 | Journal econômico idempotente |
| V019 | Operações de compra duráveis |
| V020 | Contas econômicas transacionais |
| V021 | Operações de troca Pokémon-por-Pokémon |
| V022 | Notificações persistentes |

O preço bruto, imposto de 5% e líquido usam a escala e o arredondamento da
moeda configurados. As aliases `/gts` e `/pm` usam a mesma árvore e permissão
de `/pokemarket`.

## Eventos para mercado físico futuro

`ListingCreated`, `ListingActivated`, `ListingUpdated`, `ListingSold`, `ListingTraded`, `ListingCancelled`, `ListingExpired`

A camada visual futura deve consumir o domínio via API de consulta, sem acessar tabelas diretamente.
