# PokéMarket

Módulo completo de mercado virtual de Pokémon com suporte a venda por dinheiro e troca Pokémon-por-Pokémon.

## Requisitos

- Cobblemon 1.7.3+1.21.1 (Fabric ou NeoForge)
- Banco de dados (`backend: DATABASE`)
- Módulo economy ativo

## Funcionalidades

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

### Notificações (`/pokemarket notifications`)
- Consulta de claims pendentes, compras e trocas recentes

### Administração (`/pokemarket admin`)
- Health check QUICK e FULL
- Estatísticas, listagens, operações, trocas
- Inspeção de anúncio
- Cancelamento administrativo

## Comandos

| Comando | Descrição |
|---|---|
| `/pokemarket browse [page]` | Lista anúncios ativos |
| `/pokemarket sell party <slot> <preco>` | Anunciar Pokémon da party |
| `/pokemarket sell pc <box> <slot> <preco>` | Anunciar Pokémon do PC |
| `/pokemarket trade party <slot> <json>` | Anunciar troca da party |
| `/pokemarket trade pc <box> <slot> <json>` | Anunciar troca do PC |
| `/pokemarket trade accept <id> party <slot>` | Aceitar troca |
| `/pokemarket trade accept <id> pc <box> <slot>` | Aceitar troca |
| `/pokemarket buy <id>` | Comprar anúncio |
| `/pokemarket cancel <id>` | Cancelar anúncio próprio |
| `/pokemarket claim <id>` | Retirar claim |
| `/pokemarket claim all [money\|pokemon]` | Retirar todos os claims |
| `/pokemarket claims` | Ver claims disponíveis |
| `/pokemarket history [page]` | Histórico pessoal |
| `/pokemarket notifications` | Notificações |
| `/pokemarket admin health [full]` | Health check |
| `/pokemarket admin stats` | Estatísticas |
| `/pokemarket admin listings` | Listagens recentes |
| `/pokemarket admin inspect <id>` | Detalhes do anúncio |
| `/pokemarket admin operations` | Operações de compra |
| `/pokemarket admin trades` | Trocas recentes |
| `/pokemarket admin cancel <id> <motivo>` | Cancelamento admin |
| `/pokemarket admin claims <player>` | Claims do jogador |
| `/pokemarket admin history <player>` | Auditoria do jogador |

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

## Eventos para mercado físico futuro

`ListingCreated`, `ListingActivated`, `ListingUpdated`, `ListingSold`, `ListingTraded`, `ListingCancelled`, `ListingExpired`

A camada visual futura deve consumir o domínio via API de consulta, sem acessar tabelas diretamente.
