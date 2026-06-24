# Sistema de Trabalhos e Profissões — `/jobs`

O Módulo de Trabalhos e Profissões do **BigBangEssentials** permite que jogadores escolham profissões e ganhem dinheiro e experiência (XP) ao realizar ações específicas no servidor (como minerar, pescar, caçar e construir).

---

## 1. Funcionamento Geral

O jogador pode ingressar em profissões disponíveis a partir do comando `/jobs list` e clicando em `[ENTRAR]`, ou usando `/jobs entrar <profissao>`. Cada ação executada (por exemplo, quebrar um minério configurado) concede:
1. **Dinheiro**: Depositado diretamente na conta do jogador na economia global do servidor.
2. **XP da Profissão**: Utilizado para subir o nível da profissão do jogador.
3. **Efeitos Adicionais**: O nível da profissão e habilidades passivas podem conceder bônus multiplicativos ou efeitos especiais (ex: drop duplo).

---

## 2. Configurações Globais — `jobs.json`

O arquivo `jobs.json` está localizado na pasta de configurações do mod (`config/bigbangessentials/jobs.json`) e define os limites globais e comportamento do reset diário.

```json
{
  "max-active-jobs": 2,
  "prevent-earnings-while-afk": true,
  "prevent-xp-while-afk": true,
  "daily-limit": {
    "enabled": true,
    "timezone": "America/Sao_Paulo",
    "reset-time": "00:00",
    "global": 50000.0,
    "continue-xp-after-limit": true
  }
}
```

* `max-active-jobs`: Limite padrão de profissões que um jogador pode ter ativas ao mesmo tempo (pode ser modificado por permissão VIP).
* `prevent-earnings-while-afk`: Se `true`, jogadores ausentes (AFK) não recebem dinheiro de trabalhos.
* `prevent-xp-while-afk`: Se `true`, jogadores ausentes (AFK) não ganham XP de trabalhos.
* `daily-limit`:
  * `enabled`: Ativa o limite diário global de ganhos.
  * `timezone`: Timezone considerada para calcular o reset diário.
  * `reset-time`: Horário no qual os ganhos diários são resetados.
  * `global`: Limite diário de dinheiro por jogador.
  * `continue-xp-after-limit`: Permite continuar recebendo XP mesmo após atingir o limite de dinheiro.

---

## 3. Configuração de uma Profissão

As profissões são definidas individualmente na pasta `config/bigbangessentials/jobs/` em arquivos `.json` (ex: `minerador.json`, `pescador.json`).

### Parâmetros da Profissão
* `id`: Identificador único da profissão (ex: `minerador`).
* `enabled`: Se a profissão está ativa.
* `display-name`: Nome exibido nos menus e mensagens.
* `description`: Descrição breve da profissão.
* `permission`: Permissão para o jogador poder entrar no trabalho (padrão: `jobs.profissao.<id>`).
* `unlocked-by-default`: Se `true`, todos podem usar sem permissão específica.
* `reset-progress-on-leave`: Se `true`, sair do trabalho reseta o nível, XP e habilidades do jogador.
* `max-level`: Nível máximo da profissão.
* `max-daily-earnings`: Limite diário específico desta profissão (sobrescreve o limite global).
* `money-bonus-per-level`: Percentual de bônus de dinheiro concedido por nível (ex: `0.5` = +0.5% por nível).
* `max-level-money-bonus`: Limite máximo de bônus percentual obtido via nível.
* `skill-points-every`: Quantidade de pontos de habilidade ganhos a cada nível que o jogador sobe.

### Curva de Experiência (XP)
Você pode configurar os requisitos de XP usando uma fórmula exponencial ou uma lista explícita de valores por nível:

1. **Fórmula Exponencial**:
   ```json
   "xp-curve": {
     "initial-xp": 100,
     "multiplier": 1.2
   }
   ```
   * O requisito para subir do nível `L` para `L+1` é: `initial-xp * (multiplier ^ (L - 1))`.

2. **Lista Explícita**:
   ```json
   "xp-requirements": [
     100,
     150,
     250,
     400,
     600
   ]
   ```
   * Cada posição define o XP necessário para o respectivo nível. Se o nível do jogador passar o tamanho da lista, a fórmula exponencial será usada como fallback.

---

## 4. Configuração de Ações e Recompensas

Dentro de cada arquivo de profissão, você define o mapeamento das ações e de seus respectivos alvos no objeto `"actions"`.

### Ações Suportadas
* `BREAK-BLOCK` / `BREAK_BLOCK`: Quebrar blocos (ex: minerador, lenhador).
* `PLACE-BLOCK` / `PLACE_BLOCK`: Colocar blocos (ex: construtor).
* `KILL-ENTITY` / `KILL_ENTITY`: Matar mobs/entidades (ex: caçador).
* `FISH`: Pescar itens ou peixes (ex: pescador).

### Alvos Modded e Suporte a Tags
Você pode especificar IDs completos do Minecraft Vanilla ou de outros mods, bem como tags agrupadoras:
* **ID do Registro**: `"minecraft:diamond_ore"`, `"create:zinc_ore"`
* **ID sem Namespace (fallback para vanilla)**: `"coal_ore"`
* **Tags (iniciando com `#`)**: `"#c:ores/diamond"`, `"#c:ores"`

```json
"actions": {
  "BREAK-BLOCK": {
    "minecraft:coal_ore": {
      "money": 1.00,
      "xp": 2.0
    },
    "#c:ores/diamond": {
      "money": 15.00,
      "xp": 10.0
    }
  }
}
```

---

## 5. Configuração da Árvore de Habilidades

Cada profissão pode definir sua própria árvore de habilidades passivas sob a chave `"skills"`.

### Definição da Habilidade
* `name`: Nome da habilidade.
* `description`: Descrição detalhada.
* `max-rank`: Nível/Rank máximo da habilidade.
* `point-cost`: Custo em pontos por rank.
* `required-level`: Nível mínimo do trabalho necessário para desbloquear.
* `prerequisites`: Lista de habilidades necessárias e seus respectivos ranks no formato `id:rank` (ex: `"conhecimento_mineral:3"`).
* `effects`: Efeitos e multiplicadores concedidos por rank da habilidade.

### Efeitos Suportados
* `money-multiplier`: Multiplicador aditivo de dinheiro por rank (ex: `0.01` = +1% por rank).
* `xp-multiplier`: Multiplicador aditivo de XP por rank (ex: `0.01` = +1% por rank).
* `double-drop-chance`: Chance percentual aditiva por rank de gerar drop duplo de itens.

### Exemplo de Habilidade:
```json
"skills": {
  "conhecimento_mineral": {
    "name": "Conhecimento Mineral",
    "description": "Concede +1% de XP de minerador por rank.",
    "max-rank": 5,
    "point-cost": 1,
    "required-level": 5,
    "prerequisites": [],
    "effects": {
      "xp-multiplier": 0.01
    }
  },
  "fortuna_natural": {
    "name": "Fortuna Natural",
    "description": "Concede +1% de chance por rank de drop duplo.",
    "max-rank": 5,
    "point-cost": 2,
    "required-level": 20,
    "prerequisites": ["conhecimento_mineral:3"],
    "effects": {
      "double-drop-chance": 0.01
    }
  }
}
```

---

## 6. Proteções Anti-Exploit

Para evitar trapaças e abusos com farmes automáticas ou ciclos repetitivos, o sistema inclui as seguintes proteções ativas por padrão:

### 1. Mineração (BREAK_BLOCK)
* **Blocos Colocados por Jogadores**: Blocos relevantes (como minérios) colocados por jogadores são marcados e salvos em banco de dados local. Se quebrados, **não concedem dinheiro ou XP**.
* **Automação**: Ações executadas por Fake Players ou máquinas não são recompensadas.

### 2. Construção (PLACE_BLOCK)
* **Deduplicação de Posição**: Há um cooldown persistente por coordenada para evitar o ciclo "colocar -> quebrar -> colocar" na mesma posição para ganhar XP infinito.
* **Consumo Real**: O bloco colocado deve consumir um item do inventário do jogador real para contar.

### 3. Caçador (KILL_ENTITY)
* **Spawner Mobs**: Mobs gerados a partir de blocos de Spawner (mobs de farmes) **não concedem recompensa**.
* **Participação do Jogador**: Apenas mortes com participação direta e dano recente do jogador real contam.

### 4. Pescador (FISH)
* Recompensa somente capturas válidas de pesca confirmadas pelo servidor, ignorando itens jogados ou coletados do chão.

---

## 7. VIP Multipliers (Bônus de Ganhos)

O sistema reconhece permissões numéricas para aplicar bônus VIP nos ganhos e XP. O maior valor de permissão prevalece e as permissões não são somadas.

* **Bônus de Dinheiro**: `jobs.ganhos.<percentual>` (ex: `jobs.ganhos.20` concede +20% de dinheiro, multiplicador 1.20x).
* **Bônus de XP**: `jobs.xp.<percentual>` (ex: `jobs.xp.10` concede +10% de XP, multiplicador 1.10x).
* **Bônus de Limite Diário**: `jobs.limitediario.<percentual>` (ex: `jobs.limitediario.50` concede +50% de limite diário de ganhos).

---

## 8. Comandos

### Comandos de Jogador — `/jobs`
* `/jobs`: Exibe o perfil do jogador, trabalhos ativos, nível, XP, barra de progresso, bônus VIP e ganhos diários.
* `/jobs list`: Lista todos os trabalhos disponíveis, seu estado e comandos rápidos.
* `/jobs entrar <profissao>` (ou `/jobs join <job>`): Entra em um trabalho.
* `/jobs sair <profissao>` (ou `/jobs leave <job>`): Sai de um trabalho.
* `/jobs info [profissao]`: Detalha as ações e recompensas configuradas para o trabalho.
* `/jobs progresso [profissao]`: Consulta o XP detalhado e barra de nível de um trabalho.
* `/jobs habilidades <profissao>` (ou `/jobs skills <job>`): Exibe a árvore de habilidades e botões para upgrade.
* `/jobs habilidade <profissao> desbloquear <habilidade>`: Investe pontos de habilidade para subir o rank de uma passiva.
* `/jobs ganhos`: Consulta detalhada de ganhos acumulados hoje, limite restante e tempo até o reset.
* `/jobs top <profissao>`: Exibe o ranking dos 10 jogadores com maior nível neste trabalho.
* `/jobs notificacoes <on|off>`: Alterna a exibição das mensagens rápidas de ganhos na Actionbar.
* `/jobs ajuda` (ou `/jobs help`): Mostra os comandos de ajuda.

### Comandos Administrativos — `/jobsadmin`
* `/jobsadmin reload`: Recarrega as configurações globais, trabalhos e valida arquivos sem perdas.
* `/jobsadmin info <jogador> [profissao]`: Consulta o perfil de trabalhos de outro jogador (online ou offline).
* `/jobsadmin entrar <jogador> <profissao>`: Força a entrada de um jogador em um trabalho.
* `/jobsadmin sair <jogador> <profissao>`: Força a saída de um jogador de um trabalho.
* `/jobsadmin setlevel <jogador> <profissao> <nivel>`: Define o nível de um jogador no trabalho.
* `/jobsadmin addxp <jogador> <profissao> <quantidade>`: Adiciona XP ao jogador no trabalho (dispara level-up se necessário).
* `/jobsadmin removexp <jogador> <profissao> <quantidade>`: Remove XP de um jogador.
* `/jobsadmin reset <jogador> [profissao]`: Reseta o progresso de um ou de todos os trabalhos do jogador.
* `/jobsadmin resetganhos <jogador>`: Reseta o limite diário de ganhos de outro jogador.
* `/jobsadmin pontos <jogador> <profissao> adicionar/remover <quantidade>`: Altera pontos de habilidades disponíveis do jogador.
* `/jobsadmin desbloquear <jogador> <profissao>`: Concede permissão interna de acesso à profissão para o jogador.
* `/jobsadmin bloquear <jogador> <profissao>`: Remove permissão interna de acesso à profissão do jogador.
* `/jobsadmin debug <on|off>`: Alterna o modo debug global administrativo no servidor.

---

## 9. Permissões
* `jobs.command.jobs`: Permissão básica para usar `/jobs` e ver resumo.
* `jobs.command.list`: Permissão para listar os trabalhos com `/jobs list`.
* `jobs.command.entrar`: Permissão para entrar em profissões.
* `jobs.command.sair`: Permissão para sair de profissões.
* `jobs.command.info`: Permissão para consultar detalhes de ações e progresso.
* `jobs.command.habilidades`: Permissão para acessar e desbloquear habilidades.
* `jobs.command.ganhos`: Permissão para consultar ganhos diários.
* `jobs.command.top`: Permissão para consultar os rankings de líderes.
* `jobs.profissao.<profissao_id>`: Permissão de acesso a uma profissão específica (se não for desbloqueada por padrão).
* `jobs.admin.reload`: Permissão para recarregar configurações.
* `jobs.admin.info`: Permissão para ver perfil de outros jogadores.
* `jobs.admin.modify`: Permissão para alterar nível, XP, entrar/sair forçado, pontos e bloqueios.
* `jobs.admin.reset`: Permissão para resetar progresso ou ganhos diários.
* `jobs.admin.debug`: Permissão para ativar/desativar debug administrativo.
* `jobs.admin.*`: Acesso total a todos os comandos administrativos do jobs.

---

## 10. Placeholders
Se o PlaceholderAPI estiver integrado no projeto, os seguintes placeholders estarão disponíveis para uso em painéis, chat e menus:
* `%jobs_active%`: Nomes das profissões ativas do jogador (separadas por vírgula).
* `%jobs_active_count%`: Quantidade de profissões ativas.
* `%jobs_active_limit%`: Limite de profissões ativas do jogador.
* `%jobs_total_daily_earnings%`: Total acumulado de ganhos hoje.
* `%jobs_total_daily_remaining%`: Limite de ganhos globais restante hoje.
* `%jobs_earnings_multiplier%`: Multiplicador de ganhos VIP atual.
* `%jobs_<job>_level%`: Nível do jogador no trabalho informado.
* `%jobs_<job>_xp%`: XP atual do jogador no trabalho.
* `%jobs_<job>_required_xp%`: XP necessário para subir de nível no trabalho.
* `%jobs_<job>_progress%`: Porcentagem de progresso para o próximo nível.
* `%jobs_<job>_daily_earnings%`: Ganhos específicos do trabalho hoje.
* `%jobs_<job>_daily_remaining%`: Limite restante específico do trabalho hoje.
* `%jobs_<job>_skill_points%`: Pontos de habilidades disponíveis no trabalho.

---

## 11. Exemplo de Arquivo de Profissão Completo — `minerador.json`
```json
{
  "id": "minerador",
  "enabled": true,
  "display-name": "Minerador",
  "description": "Ganhe dinheiro e experiencia minerando recursos do mundo.",
  "permission": "jobs.profissao.minerador",
  "unlocked-by-default": true,
  "reset-progress-on-leave": false,
  "max-level": 100,
  "max-daily-earnings": 15000.0,
  "money-bonus-per-level": 0.5,
  "max-level-money-bonus": 50.0,
  "skill-points-every": 1,
  "xp-curve": {
    "initial-xp": 100,
    "multiplier": 1.15
  },
  "actions": {
    "BREAK-BLOCK": {
      "minecraft:coal_ore": {
        "money": 1.00,
        "xp": 1.0
      },
      "minecraft:iron_ore": {
        "money": 3.00,
        "xp": 2.5
      },
      "minecraft:gold_ore": {
        "money": 5.00,
        "xp": 4.0
      },
      "#c:ores/diamond": {
        "money": 15.00,
        "xp": 12.0
      }
    }
  },
  "skills": {
    "conhecimento_mineral": {
      "name": "Conhecimento Mineral",
      "description": "Concede +1% de XP de mineração por rank.",
      "max-rank": 5,
      "point-cost": 1,
      "required-level": 5,
      "prerequisites": [],
      "effects": {
        "xp-multiplier": 0.01
      }
    },
    "fortuna_natural": {
      "name": "Fortuna Natural",
      "description": "Concede +1% de chance por rank de drop duplo ao quebrar ores.",
      "max-rank": 5,
      "point-cost": 2,
      "required-level": 20,
      "prerequisites": ["conhecimento_mineral:3"],
      "effects": {
        "double-drop-chance": 0.01
      }
    }
  },
  "level-up-rewards": {
    "10": {
      "commands": [
        "give %player% minecraft:iron_pickaxe 1",
        "broadcast &a%player% alcancou o nivel 10 como Minerador!"
      ]
    }
  },
  "messages": {
    "level-up": "§6§lUP! §aVocê agora é nível %level% em %job%! (+%points% pontos de habilidade)"
  }
}
```
