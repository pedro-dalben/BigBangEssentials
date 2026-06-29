# Comandos do Módulo de Crates

## /crates (alias: /crate)

Comando principal do sistema de crates. Registrado nos literais `crates` e `crate`.

### Subcomandos

| Subcomando | Permissão | Sintaxe | Descrição |
|-----------|-----------|---------|-----------|
| `editor` | `bigbangessentials.crates.editor` | `/crate editor` | Abre o editor gráfico de crates |
| `reload` | `bigbangessentials.crates.reload` | `/crate reload` | Recarrega todas as definições do disco |
| `give` | `bigbangessentials.crates.give` | `/crate give <jogador> <crate> [quantidade]` | Dá uma crate a um jogador (abre automaticamente) |
| `open` | — | `/crate open <crate>` | Abre uma crate para você mesmo |
| `openfor` | `bigbangessentials.crates.admin` | `/crate openfor <jogador> <crate> [bypass]` | Abre uma crate para outro jogador (com bypass opcional) |
| `preview` | — | `/crate preview <crate> [jogador]` | Mostra o preview das recompensas de uma crate |
| `resetcooldown` | `bigbangessentials.crates.admin` | `/crate resetcooldown <jogador> <crate>` | Reseta o cooldown de um jogador em uma crate |
| `logs` | `bigbangessentials.crates.logs` | `/crate logs [jogador] [crate]` | Visualiza logs de abertura |
| `location list` | — | `/crate location list` | Lista todas as localizações de crates |
| `location remove` | — | `/crate location remove <id>` | Remove uma localização de crate pelo ID |
| `key give` | `bigbangessentials.crates.key.give` | `/crate key give <jogador> <chave> [quantidade]` | Dá chaves virtuais a um jogador |
| `key take` | `bigbangessentials.crates.key.take` | `/crate key take <jogador> <chave> [quantidade]` | Remove chaves virtuais de um jogador |
| `key set` | `bigbangessentials.crates.key.set` | `/crate key set <jogador> <chave> [quantidade]` | Define o saldo de chaves virtuais de um jogador |
| `key inspect` | `bigbangessentials.crates.key.inspect` | `/crate key inspect [jogador]` | Inspeciona os saldos de chaves de um jogador |
| `key giveall` | `bigbangessentials.crates.giveall` | `/crate key giveall <chave> [quantidade]` | Dá chaves a todos os jogadores online |
| `key drop` | `bigbangessentials.crates.admin` | `/crate key drop <chave> <mundo> <x> <y> <z> [quantidade]` | Droppa chaves físicas no mundo |

### Exemplos

```bash
# Abrir o editor
/crate editor

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

# Ver preview
/crate preview monthly_crate

# Ver preview para outro jogador
/crate preview monthly_crate Steve

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
