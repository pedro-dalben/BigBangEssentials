# Operações administrativas

Comandos disponíveis sob `/pokemarket admin` (requer permissão `bigbangessentials.pokemarket.admin` ou OP level 3).

## Health check

| Comando | Descrição |
|---|---|
| `/pokemarket admin health` | Quick: módulo, banco, compras pendentes, claims, trocas, anúncios ativos |
| `/pokemarket admin health full` | Full: adiciona escrow órfão, claims órfãos, expirados, recovery |

## Estatísticas e listagens

| Comando | Descrição |
|---|---|
| `/pokemarket admin stats` | Totais de anúncios, compras, trocas, claims, escrow |
| `/pokemarket admin listings` | Últimas 50 listagens |
| `/pokemarket admin inspect <id>` | Detalhes completos de uma listagem |
| `/pokemarket admin operations` | Últimas 20 operações de compra |
| `/pokemarket admin trades` | Últimas 20 operações de troca |

## Ações administrativas

| Comando | Descrição |
|---|---|
| `/pokemarket admin cancel <id> <motivo>` | Cancela listagem e cria claim de devolução |
| `/pokemarket admin claims <player>` | Lista claims de um jogador |
| `/pokemarket admin history <player>` | Auditoria de ações de um jogador |

Qualquer ação que altere patrimônio (cancelamento, reembolso) deve ser registrada no audit log com motivo e identificação do administrador.
