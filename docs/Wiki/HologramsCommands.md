# BigBangHolograms — Comandos

## Comandos principais

| Comando | Descrição | Permissão |
|---------|-----------|-----------|
| `/bbholo help [topic]` | Ajuda paginada | `bigbangessentials.holograms.help` |
| `/bbholo list [page]` | Listar hologramas | `bigbangessentials.holograms.list` |
| `/bbholo create <id> [text]` | Criar na posição do jogador | `bigbangessentials.holograms.create` |
| `/bbholo clone <src> <dst> [--here]` | Clonar holograma | `bigbangessentials.holograms.clone` |
| `/bbholo rename <id> <newId>` | Renomear | `bigbangessentials.holograms.rename` |
| `/bbholo delete <id>` | Remover | `bigbangessentials.holograms.delete` |
| `/bbholo info <id>` | Informações detalhadas | `bigbangessentials.holograms.info` |
| `/bbholo enable <id>` | Ativar | `bigbangessentials.holograms.enable` |
| `/bbholo disable <id>` | Desativar | `bigbangessentials.holograms.disable` |
| `/bbholo teleport <id>` | Teleportar até o holograma | `bigbangessentials.holograms.teleport` |
| `/bbholo movehere <id>` | Mover para posição do jogador | `bigbangessentials.holograms.move` |
| `/bbholo move <id> <x> <y> <z>` | Mover para coordenadas | `bigbangessentials.holograms.move` |
| `/bbholo near [radius]` | Hologramas próximos | `bigbangessentials.holograms.list` |
| `/bbholo align <id> <axis> <other>` | Alinhar com outro | `bigbangessentials.holograms.align` |
| `/bbholo facing <id> <mode>` | Billboard mode | `bigbangessentials.holograms.info` |
| `/bbholo permission <id> [perm]` | Permissão para ver | `bigbangessentials.holograms.info` |
| `/bbholo displayrange <id> <blocos>` | Distância de exibição | `bigbangessentials.holograms.info` |
| `/bbholo updaterange <id> <blocos>` | Distância de atualização | `bigbangessentials.holograms.info` |
| `/bbholo updateinterval <id> <ticks>` | Intervalo de update | `bigbangessentials.holograms.update` |

## Comandos de linha

| Comando | Descrição |
|---------|-----------|
| `/bbholo line list <id> [page]` | Listar linhas |
| `/bbholo line add <id> [page] <conteúdo>` | Adicionar linha |
| `/bbholo line insert <id> [page] <índice> <conteúdo>` | Inserir linha |
| `/bbholo line set <id> [page] <índice> <conteúdo>` | Substituir linha |
| `/bbholo line remove <id> [page] <índice>` | Remover linha |
| `/bbholo line clone <id> [page] <índice>` | Clonar linha |
| `/bbholo line move <id> [page] <de> <para>` | Mover linha |
| `/bbholo line swap <id> [page] <l1> <l2>` | Trocar duas linhas |
| `/bbholo line clear <id> [page]` | Limpar página |
| `/bbholo line height <id> [page] <idx> <valor>` | Altura da linha |
| `/bbholo line offset <id> [page] <idx> <x> <y> <z>` | Offset |
| `/bbholo line scale <id> [page] <idx> <valor>` | Escala |
| `/bbholo line facing <id> [page] <idx> <mode>` | Facing |
| `/bbholo line permission <id> [page] <idx> [perm]` | Permissão |
| `/bbholo line flag add <id> [page] <idx> <flag>` | Flag |
| `/bbholo line flag remove <id> [page] <idx> <flag>` | Remover flag |

## Comandos de página

| Comando | Descrição |
|---------|-----------|
| `/bbholo page list <id>` | Listar páginas |
| `/bbholo page add <id> [text]` | Adicionar página |
| `/bbholo page insert <id> <índice>` | Inserir página |
| `/bbholo page clone <id> <src> <dst>` | Clonar página |
| `/bbholo page remove <id> <índice>` | Remover página |
| `/bbholo page swap <id> <p1> <p2>` | Trocar páginas |
| `/bbholo page switch <id> <page> [player]` | Trocar para página |
| `/bbholo page default <id> <page>` | Página padrão |
| `/bbholo page next <id> [player]` | Próxima página |
| `/bbholo page previous <id> [player]` | Página anterior |
| `/bbholo page rotation <id> <on/off>` | Rotação automática |
| `/bbholo page interval <id> <ticks>` | Intervalo global |
| `/bbholo page duration <id> <page> <ticks>` | Duração por página |

## Comandos de ação

| Comando | Descrição |
|---------|-----------|
| `/bbholo action list <id> <page>` | Listar ações |
| `/bbholo action add <id> <page> <trigger> <tipo> <payload>` | Adicionar ação |
| `/bbholo action remove <id> <page> <trigger> <índice>` | Remover ação |
| `/bbholo action clear <id> <page> [trigger]` | Limpar ações |

## Comandos de visibilidade

| Comando | Descrição |
|---------|-----------|
| `/bbholo visibility info <id>` | Info de visibilidade |
| `/bbholo visibility show <id> <player>` | Mostrar para jogador |
| `/bbholo visibility hide <id> <player>` | Esconder de jogador |
| `/bbholo visibility reset <id> <player>` | Resetar visibilidade |
| `/bbholo visibility permission <id> [perm]` | Permissão de visibilidade |

## Comandos administrativos

| Comando | Descrição | Permissão |
|---------|-----------|-----------|
| `/bbholo stats` | Estatísticas | `bigbangessentials.holograms.stats` |
| `/bbholo diagnostics [id]` | Diagnóstico | `bigbangessentials.holograms.diagnostics` |
| `/bbholo viewers <id>` | Viewers do holograma | `bigbangessentials.holograms.diagnostics` |
| `/bbholo save [id/all]` | Salvar | `bigbangessentials.holograms.save` |
| `/bbholo reload` | Recarregar | `bigbangessentials.holograms.reload` |
| `/bbholo reconcile` | Reconciliar | `bigbangessentials.holograms.reconcile` |
| `/bbholo export <id>` | Exportar | `bigbangessentials.holograms.export` |
| `/bbholo import <file>` | Importar | `bigbangessentials.holograms.import` |

## Flags disponíveis

- `DISABLE_UPDATING` — desativa atualizações
- `DISABLE_PLACEHOLDERS` — desativa placeholders
- `DISABLE_ANIMATIONS` — desativa animações
- `DISABLE_ACTIONS` — desativa ações
- `DISABLE_SHADOW` — remove sombra do texto
- `STATIC_CONTENT` — conteúdo nunca atualiza
- `MANUAL_VISIBILITY` — apenas visibilidade manual
- `IGNORE_SPECTATORS` — ignora espectadores

## Triggers de ação

- `LEFT_CLICK`
- `RIGHT_CLICK`
- `SHIFT_LEFT_CLICK`
- `SHIFT_RIGHT_CLICK`

## Tipos de ação

- `PLAYER_COMMAND` — comando como jogador
- `CONSOLE_COMMAND` — comando como console
- `MESSAGE` — mensagem no chat
- `BROADCAST` — broadcast
- `TELEPORT` — `x y z [world]`
- `SOUND` — `sound [volume] [pitch]`
- `NEXT_PAGE` — próxima página
- `PREVIOUS_PAGE` — página anterior
- `SET_PAGE` — página específica
