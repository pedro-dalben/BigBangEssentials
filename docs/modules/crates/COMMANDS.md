# Comandos do Módulo de Crates

## /crates (alias: /crate)

Comando principal do sistema de crates. Registrado nos literais `crates` e `crate`.

### Subcomandos

| Subcomando | Permissão | Sintaxe | Descrição |
|-----------|-----------|---------|-----------|
| `create` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate create <id> [nome]` | Cria uma nova crate |
| `edit` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate edit <crate>` | Abre o editor da crate |
| `setname` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate setname <crate> <nome>` | Altera o nome de exibição da crate |
| `setdesc` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate setdesc <crate> <descrição>` | Altera a descrição da crate |
| `toggle` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate toggle <crate>` | Alterna o estado da crate |
| `seticon` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate seticon <crate>` | Define o item do inventário como ícone da crate |
| `setopening` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate setopening <crate> <NONE|VIRTUAL|PHYSICAL>` | Define o tipo de abertura da crate |
| `setkey` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate setkey <crate> <keyId>` | Define a chave aceita pela crate |
| `setcost` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate setcost <crate> <valor>` | Define o custo econômico da crate |
| `setcooldown` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate setcooldown <crate> <ms>` | Define o cooldown da crate |
| `setperm` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate setperm <crate> <permissão>` | Define a permissão exigida pela crate |
| `addrarity` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate addrarity <crate> <id> <nome> <cor> <peso>` | Adiciona uma raridade à crate |
| `removerarity` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate removerarity <crate> <id>` | Remove uma raridade da crate |
| `addmilestone` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate addmilestone <crate> <id> <nome> <rewardId> <aberturas>` | Adiciona um milestone à crate |
| `setlocation` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate setlocation <crate>` | Vincula a crate ao bloco que você está olhando |
| `editor` | `bigbangessentials.crates.editor` | `/crate editor` | Abre o editor gráfico de crates |
| `reload` | `bigbangessentials.crates.reload` | `/crate reload` | Recarrega todas as definições do disco |
| `give` | `bigbangessentials.crates.give` | `/crate give <jogador> <crate> [quantidade]` | Dá uma crate a um jogador (abre automaticamente) |
| `open` | — | `/crate open <crate>` | Abre uma crate para você mesmo |
| `massopen` | — | `/crate massopen <crate> [quantidade]` | Abre a mesma crate várias vezes em sequência |
| `openfor` | `bigbangessentials.crates.admin` | `/crate openfor <jogador> <crate> [bypass]` | Abre uma crate para outro jogador (com bypass opcional) |
| `preview` | — | `/crate preview <crate> [jogador]` | Mostra o preview das recompensas de uma crate |
| `claim` | — | `/crate claim` | Resgata entregas pendentes da caixa de entregas do jogador |
| `resetcooldown` | `bigbangessentials.crates.admin` | `/crate resetcooldown <jogador> <crate>` | Reseta o cooldown de um jogador em uma crate |
| `logs` | `bigbangessentials.crates.logs` | `/crate logs [jogador] [crate]` | Visualiza logs de abertura |
| `location list` | — | `/crate location list` | Lista todas as localizações de crates |
| `location remove` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate location remove <id>` | Remove uma localização de crate pelo ID |
| `key give` | `bigbangessentials.crates.key.give` | `/crate key give <jogador> <chave> [quantidade]` | Dá chaves virtuais a um jogador |
| `key take` | `bigbangessentials.crates.key.take` | `/crate key take <jogador> <chave> [quantidade]` | Remove chaves virtuais de um jogador |
| `key set` | `bigbangessentials.crates.key.set` | `/crate key set <jogador> <chave> [quantidade]` | Define o saldo de chaves virtuais de um jogador |
| `key inspect` | `bigbangessentials.crates.key.inspect` | `/crate key inspect [jogador]` | Inspeciona os saldos de chaves de um jogador |
| `key create` | `bigbangessentials.crates.editor` | `/crate key create <id> [nome]` | Cria uma nova chave |
| `key editor` | `bigbangessentials.crates.editor` | `/crate key editor` | Abre o editor de chaves |
| `key setname` | `bigbangessentials.crates.editor` | `/crate key setname <id> <nome>` | Altera o nome da chave |
| `key settype` | `bigbangessentials.crates.editor` | `/crate key settype <id> <virtual|physical>` | Altera o tipo da chave |
| `key toggle` | `bigbangessentials.crates.editor` | `/crate key toggle <id>` | Alterna o estado da chave |
| `key seticon` | `bigbangessentials.crates.editor` | `/crate key seticon <id>` | Define o item na mão como ícone físico da chave |
| `key addcrate` | `bigbangessentials.crates.editor` | `/crate key addcrate <id> <crateKey>` | Vincula uma crate compatível à chave |
| `key giveall` | `bigbangessentials.crates.giveall` | `/crate key giveall <chave> [quantidade]` | Dá chaves a todos os jogadores online |
| `key drop` | `bigbangessentials.crates.admin` | `/crate key drop <chave> <mundo> <x> <y> <z> [quantidade]` | Droppa chaves físicas no mundo |
| `reward create` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward create <crate> <id> <nome> <rarityId>` | Cria uma nova recompensa |
| `reward setname` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setname <crate> <rewardId> <nome>` | Altera o nome da recompensa |
| `reward setweight` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setweight <crate> <rewardId> <peso>` | Altera o peso da recompensa |
| `reward setrarity` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setrarity <crate> <rewardId> <rarityId>` | Altera a raridade da recompensa |
| `reward toggle` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward toggle <crate> <rewardId>` | Alterna o estado da recompensa |
| `reward seticon` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward seticon <crate> <rewardId>` | Define o item na mão como ícone da recompensa |

### Exemplos

```bash
# Abrir o editor
/crate editor

# Criar uma crate
/crate create minha_crate "Minha Crate"

# Criar uma chave
/crate key create chave_minha_crate "Chave da Minha Crate"

# Abrir o editor de uma crate
/crate edit minha_crate

# Alterar a descrição de uma crate
/crate setdesc minha_crate "Uma crate especial"

# Vincular a crate ao bloco que você está olhando
/crate setlocation minha_crate

# Criar uma recompensa
/crate reward create minha_crate espada_rara "Espada Rara" raro

# Recarregar definições
/crate reload

# Dar uma crate a um jogador
/crate give Steve vip_crate

# Dar 5 crates a um jogador
/crate give @a event_crate 3

# Abrir uma crate para si
/crate open mistery_box

# Abrir uma crate para outro jogador (com bypass)
/crate openfor Alex legendary_crate true

# Abrir a mesma crate várias vezes
/crate massopen monthly_crate 10

# Ver preview
/crate preview monthly_crate

# Ver preview para outro jogador
/crate preview monthly_crate Steve

# Resgatar entregas pendentes
/crates claim

# Resetar cooldown
/crate resetcooldown Steve daily_crate

# Ver logs
/crate logs

# Ver logs de um jogador específico
/crate logs Steve

# Ver logs de um jogador em uma crate específica
/crate logs Steve weekly_crate

# Listar localizações
/crate location list

# Remover uma localização
/crate location remove 550e8400-e29b-41d4-a716-446655440000

# Dar chave a um jogador
/crate key give Steve vip_key 5

# Remover chave de um jogador
/crate key take Steve vip_key 2

# Definir saldo de chave
/crate key set Steve vip_key 10

# Inspecionar chaves de um jogador
/crate key inspect Steve

# Dar chave a todos online
/crate key giveall event_key 1

# Dropar chaves no mundo
/crate key drop rare_key minecraft:overworld 100 64 200 3
```

## /givekey

Comando direto para dar chaves virtuais a jogadores.

| Item | Detalhe |
|------|---------|
| **Descrição** | Dá chaves virtuais a um ou mais jogadores |
| **Permissão** | `bigbangessentials.crates.key.give` |
| **Sintaxe** | `/givekey <chave> <jogador> [quantidade]` |
| **Alias** | — |
| **Fonte** | `ADMIN_COMMAND` |

### Exemplos

```bash
/givekey chave_vip Steve
/givekey chave_vip Steve 10
/givekey chave_evento @a 5
```

## /keygive

Alias para `/givekey`, mesmo comportamento.

| Item | Detalhe |
|------|---------|
| **Descrição** | Dá chaves virtuais a um ou mais jogadores |
| **Permissão** | `bigbangessentials.crates.key.give` |
| **Sintaxe** | `/keygive <chave> <jogador> [quantidade]` |
| **Alias** | `/givekey` |
| **Fonte** | `ADMIN_COMMAND` |

### Exemplos

```bash
/keygive chave_vip Steve
/keygive chave_vip Steve 10
/keygive chave_evento @a 5
```
