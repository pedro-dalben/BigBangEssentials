# Permissões do Módulo de Crates

Todas as permissões seguem o padrão `bigbangessentials.crates.*`.

| Permissão | Descrição | Default |
|-----------|-----------|---------|
| `bigbangessentials.crates.use` | Permite usar/interagir com crates | OP |
| `bigbangessentials.crates.preview` | Permite visualizar preview de crates | OP |
| `bigbangessentials.crates.open.<crate>` | Permite abrir uma crate específica (substitua `<crate>` pelo ID da crate) | OP |
| `bigbangessentials.crates.bypass.cooldown` | Ignora cooldown ao abrir crates | OP |
| `bigbangessentials.crates.bypass.requirements` | Ignora todos os requisitos (chaves, permissão, custo) | OP |
| `bigbangessentials.crates.admin` | Acesso administrativo completo ao sistema de crates | OP |
| `bigbangessentials.crates.editor` | Permite abrir o editor gráfico de crates | OP |
| `bigbangessentials.crates.manage` | Permite gerenciar crates (criar, editar, deletar) | OP |
| `bigbangessentials.crates.give` | Permite dar crates a jogadores | OP |
| `bigbangessentials.crates.giveall` | Permite dar chaves a todos os jogadores online | OP |
| `bigbangessentials.crates.key.give` | Permite dar chaves virtuais a jogadores | OP |
| `bigbangessentials.crates.key.take` | Permite remover chaves virtuais de jogadores | OP |
| `bigbangessentials.crates.key.set` | Permite definir o saldo de chaves virtuais de jogadores | OP |
| `bigbangessentials.crates.key.inspect` | Permite inspecionar saldos de chaves de jogadores | OP |
| `bigbangessentials.crates.logs` | Permite visualizar logs de abertura de crates | OP |
| `bigbangessentials.crates.reload` | Permite recarregar as definições do disco | OP |

## Permissões de Recompensa (por crate)

Cada recompensa pode exigir permissão específica definida no campo `requiredPermission` da recompensa. O sistema verifica as seguintes permissões durante a elegibilidade:

| Permissão | Descrição |
|-----------|-----------|
| `bigbangessentials.crates.reward.*` | Permite receber qualquer recompensa |
| `bigbangessentials.crates.reward.<jogador>` | Permissão específica por nome de jogador |

## Permissões de Chave

Cada chave pode definir um `requiredPermission` no campo correspondente da `KeyDefinition`. Se definido, o jogador precisa dessa permissão para receber ou usar a chave.

## Bloqueio de Bloco (Proteção)

A quebra de blocos vinculados a crates requer permissão de administrador (nível 2+ de op) ou a permissão `bigbangessentials.crates.admin`. Jogadores sem permissão têm o evento de quebra cancelado.

## Notas

- O prefixo padrão de todas as permissões é `bigbangessentials.crates.*`
- Permissões são verificadas através da `PermissionAPI` do BigBangEssentials
- Jogadores com nível de op 4 (`source.hasPermission(4)`) bypassam todas as verificações de permissão
- O wildcard `bigbangessentials.crates.*` pode ser usado para conceder todas as permissões de crates
