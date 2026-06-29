# Sistema de Animação de Crates

## Visão Geral

O sistema de animação (`CrateAnimationHandler`) gerencia animações de abertura de crates. Há dois tipos de animação definidos por `CrateOpeningType`:

- **VIRTUAL** — Animação em GUI com rolagem de itens, exclusiva do jogador
- **PHYSICAL** — Animação no bloco da crate com partículas e sons, visível para todos próximos
- **NONE** — Sem animação, entrega instantânea

## Animação Virtual (`VirtualOpeningMenu`)

### Fluxo

1. O jogador abre a crate
2. Um menu GUI 9x6 é aberto com:
   - Itens de recompensa ocupando os slots centrais (exibidos aleatoriamente)
   - Botão **Skip** (slot 48) — Uma seta para pular a animação
   - Botão **Coletar** (slot 50) — Inicialmente desabilitado
3. Durante a animação, os ícones das recompensas alternam nos slots (efeito de rolagem)
4. Sons de abertura são tocados
5. Ao final da animação (ou ao pular):
   - A recompensa é revelada no slot central (22)
   - O botão **Coletar** é ativado (esmeralda)
   - A recompensa é entregue ao jogador via `RewardService.deliverReward()`
6. O jogador clica em **Coletar** para fechar o menu

### Slots do Menu

```
[ ][ ][ ][ ][ ][ ][ ][ ][ ]   Linha 0
[ ][R][R][R][R][R][R][R][ ]   Linha 1  (R = itens rolando)
[ ][R][R][R][R][R][R][R][ ]   Linha 2
[ ][R][R][R][R][R][R][R][ ]   Linha 3
[ ][R][R][R][R][R][R][R][ ]   Linha 4
[ ][ ][ ][ ][S][ ][ ][C][ ]   Linha 5  (S=Skip, C=Coletar)
```

### Skip

- Pular a animação é permitido por padrão (`allowSkip: true`)
- O jogador clica no slot 48 (seta) para pular
- Ao pular, a animação avança imediatamente para o estado de recompensa revelada

## Animação Física

### Fluxo

1. O jogador interage com o bloco da crate
2. A animação é iniciada no `CrateAnimationHandler`
3. Durante a animação (a cada 5 ticks):
   - Partículas em formato **SPIRAL** são spawnadas ao redor do bloco
   - Som de início (`startSound`) é tocado
4. Ao finalizar:
   - Partículas em formato **COLUMN** são spawnadas no bloco
   - Som de level up é tocado
   - A recompensa é entregue ao jogador

## Configuração da Animação (`CrateAnimationConfig`)

| Campo | Tipo | Padrão | Descrição |
|-------|------|--------|-----------|
| `allowSkip` | boolean | `true` | Permite pular a animação |
| `durationTicks` | int | `60` | Duração total da animação em ticks (3 segundos) |
| `startSound` | String | `minecraft:block.chest.open` | Som ao iniciar |
| `tickSound` | String | `""` | Som a cada tick (opcional) |
| `endSound` | String | `minecraft:entity.player.levelup` | Som ao finalizar |
| `rewardSound` | String | `minecraft:entity.experience_orb.pickup` | Som ao revelar recompensa |
| `showRollingItems` | boolean | `true` | Mostra itens rolando na virtual |
| `rollingSpeed` | int | `2` | Ticks entre troca de itens |
| `highlightDurationTicks` | int | `40` | Duração do destaque da recompensa |
| `particleConfig` | objeto | (padrão) | Configuração de partículas da animação |

### Configuração de Partículas (`CrateParticleConfig`)

| Campo | Tipo | Padrão | Descrição |
|-------|------|--------|-----------|
| `particleType` | String | `minecraft:enchant` | Tipo de partícula |
| `shape` | enum | `CIRCLE` | Formato: `CIRCLE`, `SPIRAL`, `COLUMN`, `AURA`, `NONE` |
| `frequencyTicks` | int | `1` | Partículas a cada N ticks |
| `particleCount` | int | `5` | Quantidade por spawn |
| `radius` | double | `1.0` | Raio do círculo/espiral |
| `height` | double | `1.5` | Altura do efeito |
| `speed` | double | `0.1` | Velocidade das partículas |
| `maxDistance` | int | `32` | Distância máxima de renderização |
| `onlyNearbyPlayers` | boolean | `true` | Só spawna perto de jogadores |

## Formatos de Partículas

### CIRCLE
Partículas em círculo horizontal ao redor do centro do bloco.

```
    . . .
  .       .
  .   +   .
  .       .
    . . .
```

### SPIRAL
Partículas em espiral ascendente ao redor do bloco.

```
      .
    . .
  .   .
+ . .
```

### COLUMN
Partículas em coluna vertical subindo do bloco.

```
    .
    .
    .
    +
```

### AURA
Partículas aleatórias distribuídas ao redor do bloco em formato de aura.

```
  .   .   .
.   +   .   .
  .   .   .
```

## Som

Sons são resolvidos através do registro de sons do Minecraft usando `BuiltInRegistries.SOUND_EVENT`. Os sons podem ser configurados com qualquer `ResourceLocation` válido, como:

- `minecraft:block.chest.open`
- `minecraft:entity.player.levelup`
- `minecraft:entity.experience_orb.pickup`
- `minecraft:block.note_block.harp`

Se um som não for encontrado ou for inválido, ele é ignorado silenciosamente (apenas log de debug).

## Gerenciamento de Estado

O `CrateAnimationHandler` mantém um mapa de animações ativas (`activeAnimations`) e:

- **Inicia animação**: Adiciona ao mapa e marca `running = true`
- **Tick**: Processa cada animação ativa a cada tick do servidor
- **Remove**: Quando a animação completa ou é pulada
- **Shutdown**: No desligamento do servidor, entrega recompensas pendentes e fecha menus
- **Eventos**: Ao sair do servidor ou morrer, a animação do jogador é removida
- **Prevenção**: Jogador não pode iniciar nova animação se já estiver em uma

## Partículas Idle (Fora de Animação)

As partículas idle são gerenciadas pelo `CrateParticleManager`, separadamente do sistema de animação. Elas ficam ativas enquanto o servidor estiver rodando, spawnando partículas nos blocos de crate em intervalos configurados (`idleParticleConfig` em `CrateVisualConfig`).
