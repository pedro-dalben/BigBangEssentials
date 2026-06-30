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
| `rarity setname` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate rarity setname <crate> <rarityId> <nome>` | Altera o nome de exibição da raridade |
| `rarity setcolor` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate rarity setcolor <crate> <rarityId> <cor>` | Altera a cor usada na raridade |
| `rarity setweight` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate rarity setweight <crate> <rarityId> <peso>` | Altera o peso da raridade |
| `rarity seticon` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate rarity seticon <crate> <rarityId>` | Define o item na mão como ícone da raridade |
| `rarity setlore` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate rarity setlore <crate> <rarityId> <linha1 \| linha2>` | Define a lore exibida na raridade |
| `rarity toggle` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate rarity toggle <crate> <rarityId>` | Alterna o estado ativo da raridade |
| `rarity setpriority` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate rarity setpriority <crate> <rarityId> <prioridade>` | Ajusta a prioridade de seleção da raridade |
| `rarity setdisplayorder` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate rarity setdisplayorder <crate> <rarityId> <ordem>` | Ajusta a ordem de exibição da raridade |
| `addmilestone` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate addmilestone <crate> <id> <nome> <rewardId> <aberturas>` | Adiciona um milestone à crate |
| `milestone setname` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate milestone setname <crate> <milestoneId> <nome>` | Altera o nome de um milestone |
| `milestone setdescription` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate milestone setdescription <crate> <milestoneId> <descricao>` | Altera a descrição de um milestone |
| `milestone setreward` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate milestone setreward <crate> <milestoneId> <rewardId>` | Altera a recompensa vinculada ao milestone |
| `milestone setopenings` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate milestone setopenings <crate> <milestoneId> <aberturas>` | Altera o número de aberturas exigidas |
| `milestone toggle` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate milestone toggle <crate> <milestoneId>` | Alterna o estado ativo do milestone |
| `milestone setrepeatable` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate milestone setrepeatable <crate> <milestoneId> <true|false>` | Define se o milestone é repetível |
| `milestone setdisplayorder` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate milestone setdisplayorder <crate> <milestoneId> <ordem>` | Ajusta a ordem de exibição do milestone |
| `milestone remove` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate milestone remove <crate> <milestoneId>` | Remove um milestone da crate |
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
| `key setlore` | `bigbangessentials.crates.editor` | `/crate key setlore <id> <linha1 \| linha2>` | Define a lore exibida na chave |
| `key setperm` | `bigbangessentials.crates.editor` | `/crate key setperm <id> <permissão \| clear>` | Define ou remove a permissão necessária |
| `key setgivesound` | `bigbangessentials.crates.editor` | `/crate key setgivesound <id> <som \| clear>` | Define o som tocado ao entregar a chave |
| `key settakesound` | `bigbangessentials.crates.editor` | `/crate key settakesound <id> <som \| clear>` | Define o som tocado ao remover a chave |
| `key setgivecommands` | `bigbangessentials.crates.editor` | `/crate key setgivecommands <id> <cmd1 \| cmd2>` | Substitui a lista de comandos executados ao entregar a chave |
| `key addgivecommand` | `bigbangessentials.crates.editor` | `/crate key addgivecommand <id> <comando>` | Adiciona um comando à entrega da chave |
| `key cleargivecommands` | `bigbangessentials.crates.editor` | `/crate key cleargivecommands <id>` | Remove todos os comandos de entrega |
| `key setcrates` | `bigbangessentials.crates.editor` | `/crate key setcrates <id> <crate1 \| crate2>` | Substitui a lista de crates compatíveis |
| `key removecrate` | `bigbangessentials.crates.editor` | `/crate key removecrate <id> <crateKey>` | Remove uma crate compatível da chave |
| `key giveall` | `bigbangessentials.crates.giveall` | `/crate key giveall <chave> [quantidade]` | Dá chaves a todos os jogadores online |
| `key drop` | `bigbangessentials.crates.admin` | `/crate key drop <chave> <mundo> <x> <y> <z> [quantidade]` | Droppa chaves físicas no mundo |
| `reward create` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward create <crate> <id> <nome> <rarityId>` | Cria uma nova recompensa |
| `reward setname` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setname <crate> <rewardId> <nome>` | Altera o nome da recompensa |
| `reward setweight` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setweight <crate> <rewardId> <peso>` | Altera o peso da recompensa |
| `reward setrarity` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setrarity <crate> <rewardId> <rarityId>` | Altera a raridade da recompensa |
| `reward toggle` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward toggle <crate> <rewardId>` | Alterna o estado da recompensa |
| `reward seticon` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward seticon <crate> <rewardId>` | Define o item na mão como ícone da recompensa |
| `reward setitems` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setitems <crate> <rewardId>` | Substitui os itens da recompensa pelo item na mão |
| `reward additem` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward additem <crate> <rewardId>` | Adiciona o item na mão à lista da recompensa |
| `reward clearitems` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward clearitems <crate> <rewardId>` | Remove todos os itens da recompensa |
| `reward setcommands` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setcommands <crate> <rewardId> <cmd1 \| cmd2>` | Substitui a lista de comandos da recompensa |
| `reward addcommand` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward addcommand <crate> <rewardId> <comando>` | Adiciona um comando à recompensa |
| `reward clearcommands` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward clearcommands <crate> <rewardId>` | Remove todos os comandos da recompensa |
| `reward remove` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward remove <crate> <rewardId>` | Remove uma recompensa da crate |
| `reward duplicate` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward duplicate <crate> <rewardId> <novoId> [nome]` | Duplica uma recompensa existente |
| `reward settype` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward settype <crate> <rewardId> <ITEM\|COMMAND>` | Altera o tipo da recompensa |
| `reward setlore` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setlore <crate> <rewardId> <linha1 \| linha2>` | Define a lore exibida na recompensa |
| `reward setperm` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setperm <crate> <rewardId> <permissão\|clear>` | Define ou remove a permissão necessária |
| `reward setvisible` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setvisible <crate> <rewardId> <true|false>` | Controla se a recompensa aparece no preview |
| `reward setmilestoneonly` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setmilestoneonly <crate> <rewardId> <true|false>` | Restringe a recompensa a milestones |
| `reward setbroadcast` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setbroadcast <crate> <rewardId> <true|false>` | Ativa ou desativa broadcast ao ganhar a recompensa |
| `reward setbroadcastmsg` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setbroadcastmsg <crate> <rewardId> <mensagem>` | Define a mensagem de broadcast |
| `reward setplayermsg` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setplayermsg <crate> <rewardId> <mensagem>` | Define a mensagem enviada ao jogador |
| `reward setdisplayorder` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setdisplayorder <crate> <rewardId> <ordem>` | Ajusta a ordem de exibição |
| `reward setgloballimit` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setgloballimit <crate> <rewardId> <limite>` | Ajusta o limite global |
| `reward setplayerlimit` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setplayerlimit <crate> <rewardId> <limite>` | Ajusta o limite por jogador |
| `reward setblockingperms` | `bigbangessentials.crates.manage` / `bigbangessentials.crates.editor` | `/crate reward setblockingperms <crate> <rewardId> <perm1 \| perm2>` | Define permissões bloqueadoras |

### Exemplos

```bash
# Abrir o editor
/crate editor

# Criar uma crate
/crate create minha_crate "Minha Crate"

# Criar uma chave
/crate key create chave_minha_crate "Chave da Minha Crate"

# Ajustar metadata avançada de uma chave
/crate key setlore chave_minha_crate "Linha 1 | Linha 2"
/crate key setperm chave_minha_crate bigbangessentials.vip
/crate key setgivesound chave_minha_crate minecraft:item.trident.throw
/crate key settakesound chave_minha_crate minecraft:item.trident.return
/crate key setgivecommands chave_minha_crate "say {player} recebeu a chave | tellraw {player} {\"text\":\"Entrega realizada\"}"
/crate key addgivecommand chave_minha_crate "say Chave entregue"
/crate key cleargivecommands chave_minha_crate
/crate key setcrates chave_minha_crate vip_crate | event_crate
/crate key removecrate chave_minha_crate vip_crate

# Abrir o editor de uma crate
/crate edit minha_crate

# Alterar a descrição de uma crate
/crate setdesc minha_crate "Uma crate especial"

# Vincular a crate ao bloco que você está olhando
/crate setlocation minha_crate

# Criar uma recompensa
/crate reward create minha_crate espada_rara "Espada Rara" raro

# Editar raridades existentes
/crate rarity setname minha_crate raro "Raro"
/crate rarity setcolor minha_crate raro "#FFD700"
/crate rarity setweight minha_crate raro 30
/crate rarity seticon minha_crate raro
/crate rarity setlore minha_crate raro "Linha 1 | Linha 2"
/crate rarity toggle minha_crate raro
/crate rarity setpriority minha_crate raro 10
/crate rarity setdisplayorder minha_crate raro 1

# Ajustar metadata avançada da recompensa
/crate reward setitems minha_crate espada_rara
/crate reward additem minha_crate espada_rara
/crate reward clearitems minha_crate espada_rara
/crate reward setcommands minha_crate recompensa_cmd "say {player} | give {player} diamond 1"
/crate reward addcommand minha_crate recompensa_cmd "say premiado"
/crate reward clearcommands minha_crate recompensa_cmd
/crate reward settype minha_crate espada_rara COMMAND
/crate reward setlore minha_crate espada_rara "Linha 1 | Linha 2"
/crate reward setperm minha_crate espada_rara bigbangessentials.vip
/crate reward setvisible minha_crate espada_rara true
/crate reward setdisplayorder minha_crate espada_rara 5
/crate reward duplicate minha_crate espada_rara espada_rara2 "Espada Rara 2"
/crate reward remove minha_crate espada_rara2

# Gerenciar milestone existente
/crate addmilestone minha_crate marco10 "10 Aberturas" espada_rara 10
/crate milestone setname minha_crate marco10 "Marco 10"
/crate milestone setdescription minha_crate marco10 "Premio por 10 aberturas"
/crate milestone setreward minha_crate marco10 espada_rara
/crate milestone setopenings minha_crate marco10 10
/crate milestone toggle minha_crate marco10
/crate milestone setrepeatable minha_crate marco10 true
/crate milestone setdisplayorder minha_crate marco10 1
/crate milestone remove minha_crate marco10

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
