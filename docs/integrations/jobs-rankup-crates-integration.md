# BigBangEssentials — Integração RankUp / Jobs / Crates

## Visão Geral da Arquitetura

O ecossistema BigBangEssentials conecta três módulos centrais em um fluxo de progressão contínuo:

```text
                     ┌──────────────────────────────────────────┐
                     │              RankUp System              │
                     │  (Patentes, Permissões, Progressão)     │
                     └────┬──────────────┬─────────────────────┘
                          │              │
                          ▼              ▼
          ┌──────────────────────┐  ┌──────────────────────────┐
          │  Job Milestones      │  │  UnlockRequirements     │
          │  (Slots liberados    │  │  (Rank/permissão para   │
          │   por rank)          │  │   licença de job)       │
          └──────────┬───────────┘  └──────────┬───────────────┘
                     │                          │
                     ▼                          ▼
          ┌───────────────────────────────────────────────┐
          │              Jobs System                      │
          │  (Profissões, XP, Moedas, Licenças, Slots)    │
          │                                               │
          │  ┌─────────────────────────────────────────┐  │
          │  │  CrateRewardDefinition                  │  │
          │  │  (Por ação: chance de chave de crate)   │  │
          │  └────────────────┬────────────────────────┘  │
          └───────────────────┼───────────────────────────┘
                              │
                              ▼
          ┌───────────────────────────────────────────────┐
          │              Crates System                    │
          │  (Chaves físicas/virtuais, raridades, loot)   │
          └───────────────────────────────────────────────┘
```

## 1. RankUp → Jobs (Milestones de Carreira)

### 1.1 Como funciona

O sistema `JobRankMilestoneService` monitora o rank atual do jogador no `RankupManager`. Quando um jogador atinge um rank que corresponde a um milestone configurado, os slots de profissão correspondentes são liberados.

**Arquivo**: `config/bigbangessentials/jobs/milestones.json`

```json
{
  "schema-version": 2,
  "milestones": {
    "novice": {
      "id": "novice",
      "display-name": "Novato",
      "required-rank-id": "novice",
      "required-rank-order": 1,
      "unlocked-slots": ["COMMON_PRIMARY"],
      "eligible-jobs": ["miner","woodcutter","farmer","builder","blacksmith","crafter","explorer","ranger","culinarian","magician","fisherman"]
    },
    "veteran": {
      "id": "veteran",
      "display-name": "Veterano",
      "required-rank-id": "veteran",
      "required-rank-order": 2,
      "unlocked-slots": ["COMMON_SECONDARY"],
      "eligible-jobs": ["miner","woodcutter","farmer","builder","blacksmith","crafter","explorer","ranger","culinarian","magician","fisherman"]
    },
    "adept": {
      "id": "adept",
      "display-name": "Adepto",
      "required-rank-id": "adept",
      "required-rank-order": 3,
      "unlocked-slots": ["POKEMON_SPECIALIZATION"],
      "eligible-jobs": ["researcher","breeder","trainer","pasture_keeper","paleontologist","raider"]
    }
  }
}
```

**Campos do Milestone**:

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | string | Identificador único do milestone |
| `display-name` | string | Nome de exibição |
| `required-rank-id` | string | ID do rank no sistema RankUp que ativa este milestone |
| `required-rank-order` | int | Ordem mínima do rank (alternativa numérica ao ID) |
| `unlocked-slots` | string[] | Slots liberados ao atingir este milestone |
| `eligible-jobs` | string[] | Profissões que podem usar os slots liberados |

### 1.2 Slots de Profissão

**Arquivo**: `config/bigbangessentials/jobs/slots.json`

```json
{
  "schema-version": 2,
  "slots": {
    "COMMON_PRIMARY": {
      "slot-type": "COMMON_PRIMARY",
      "display-name": "Profissão Primária",
      "category": "COMMON",
      "cooldown-minutes": 30
    },
    "COMMON_SECONDARY": {
      "slot-type": "COMMON_SECONDARY",
      "display-name": "Profissão Secundária",
      "category": "COMMON",
      "cooldown-minutes": 60
    },
    "POKEMON_SPECIALIZATION": {
      "slot-type": "POKEMON_SPECIALIZATION",
      "display-name": "Especialização Pokémon",
      "category": "POKEMON_SPECIALIZATION",
      "cooldown-minutes": 120
    }
  }
}
```

### 1.3 Fluxo de Progressão

```
Jogador entra no servidor
  → RankupManager.getCurrentRank(player)
    → JobRankMilestoneService.loadPlayer(player)
      → Itera milestones configurados
        → Se currentOrder >= requiredRankOrder
          → Libera slots + profissões elegíveis
```

## 2. RankUp → Jobs (UnlockRequirements)

### 2.1 Definição

Cada profissão pode ter `unlock-requirements` que definem condições adicionais (além dos milestones) para que um jogador possa obter a licença da profissão.

**Classe**: `UnlockRequirements.java`
- `unlockedByDefault` (boolean) — Se true, ignora todas as outras verificações
- `requiredRankId` (string|null) — ID do rank específico necessário
- `requiredRankOrder` (int) — Ordem mínima do rank
- `permission` (string|null) — Permissão LuckPerms necessária

### 2.2 Exemplo de Configuração

```json
{
  "id": "raider",
  "unlock-requirements": {
    "unlocked-by-default": false,
    "required-rank-id": "adept",
    "required-rank-order": 3,
    "permission": "jobs.profession.raider"
  }
}
```

### 2.3 Lógica de Avaliação

No `JobLicenseService.getLicenseStatus()` e `JobCommandService.joinJob()`:

1. Se `unlockedByDefault == true` → liberado imediatamente
2. Verifica milestones de carreira (slots disponíveis)
3. Se `requiredRankId != null` → verifica `JobRankMilestoneService.isAtOrAboveRank(player, rankId)`
4. Se `requiredRankOrder > 0` → verifica se ordem atual >= requiredRankOrder
5. Se `permission != null` → verifica se jogador tem a permissão via LuckPerms
6. Todas as condições são AND (todas devem ser satisfeitas)

## 3. Jobs → Crates (Per-Job Crate Rewards)

### 3.1 Definição

Cada profissão pode definir uma lista de `crate-rewards`: recompensas em chaves de crate que podem ser obtidas ao executar ações válidas na profissão.

**Classe**: `CrateRewardDefinition.java`
- `actions` (string[]) — Lista de action types que podem ativar esta recompensa (vazio = qualquer ação)
- `keyId` (string) — ID da chave a ser concedida
- `chance` (double) — Probabilidade 0.0–1.0 (0.005 = 0.5%)
- `amount` (int) — Quantidade de chaves concedidas (≥ 1)
- `minimumJobLevel` (int) — Nível mínimo da profissão (≥ 1)
- `requiredRankId` (string|null) — Rank mínimo do RankUp para esta recompensa específica
- `dailyLimit` (int) — Limite diário por jogador (0 = bloqueado, 3 = padrão)
- `cooldownSeconds` (long) — Cooldown entre concessões (1800 = 30 min)

### 3.2 Exemplo de Configuração

```json
{
  "id": "researcher",
  "crate-rewards": [
    {
      "actions": ["POKEMON-CAPTURED"],
      "key-id": "researcher_key",
      "chance": 0.02,
      "amount": 1,
      "minimum-job-level": 5,
      "required-rank-id": "researcher_rank",
      "daily-limit": 5,
      "cooldown-seconds": 3600
    },
    {
      "actions": ["DEX-ENTRY-ADDED"],
      "key-id": "researcher_key",
      "chance": 0.05,
      "amount": 2,
      "minimum-job-level": 1,
      "daily-limit": 10
    },
    {
      "actions": [],
      "key-id": "craft_key",
      "chance": 0.005,
      "amount": 1,
      "minimum-job-level": 1,
      "daily-limit": 3,
      "cooldown-seconds": 1800
    }
  ]
}
```

### 3.3 Processamento (JobRewardRollService)

Quando uma ação é processada pelo pipeline:

1. `JobRewardApplier` chama `processActionRewards(uuid, jobDef, level, actionType, weight)`
2. Para cada `CrateRewardDefinition` na lista:
   - Verifica se `matchesAction(actionType)` (case-insensitive, lista vazia = qualquer ação)
   - Verifica se `level >= minimumJobLevel`
   - Verifica se `requiredRankId` é satisfeito (se presente)
   - Verifica limites via `JobRewardLimitService.checkLimit()`
   - Aplica rolagem de chance: `random.nextDouble() < chance`
   - Se aprovado: concede `amount` × `keyId` via `SpecialistKeyService`
3. Se a lista `crate-rewards` estiver vazia → fallback legado: `JobKeyDropRule.defaultConfig()` (0.5% base + 0.02%/level)

### 3.4 Campos do CrateRewardDefinition

| Campo | Tipo | Default | Validação |
|-------|------|---------|-----------|
| `actions` | string[] | `[]` (qualquer) | Case-insensitive, valores conhecidos geram warning |
| `key-id` | string | `"craft_key"` | Não pode ser vazio |
| `chance` | double | `0.005` | 0.0–1.0 |
| `amount` | int | `1` | ≥ 1 |
| `minimum-job-level` | int | `1` | ≥ 1 |
| `required-rank-id` | string\|null | `null` | Opcional, validado contra ranks existentes |
| `daily-limit` | int | `3` | ≥ 0 (0 = bloqueado permanentemente) |
| `cooldown-seconds` | long | `1800` | ≥ 0 |

## 4. RankUp (Config Colors Fix)

### 4.1 Problema

Cores configuradas com `&` (ex: `&a`, `&6`) não eram traduzidas para o código de formatação do Minecraft (`\u00a7`, `§`), resultando em mensagens sem cor e nomes de rank exibindo o código literal.

### 4.2 Solução

O método `RankupConfig.translateColors()` é chamado durante o parsing (`parseAndValidate`) para todos os campos de texto que podem conter cores:

```java
public static String translateColors(String input) {
    return input != null ? input.replace('&', '\u00a7') : null;
}
```

**Campos afetados**:
- `ladder.display-name`
- `rank.display-name`
- `rank.description[]`
- `task.display-name`
- `task.description[]`
- `actions.broadcast`
- Mensagens e placeholders em menus

### 4.3 Código Limpo

Os chamadores de `stripColor()` nos menus e serviços foram revisados para **não remover** as cores após a tradução:

- `RankupMenuSupport` — strip() não remove códigos `§`
- `RankupPromotionService` — placeholders preservam cores
- `RankupFormatter` — nomes de tarefas mantêm cores

## 5. FLuxo Completo de Progressão

```
1. Jogador faz ações no jogo (minerar, capturar, etc.)
2. RankupTaskTracker registra progresso das tarefas
3. Jogador cumpre requisitos → /rankup → promoção
4. RankupPromotionService.transactionalPromotion():
   a. Valida requisitos
   b. Salva PREPARED transaction (DB)
   c. Withdraw money + debita gems
   d. Update LuckPerms (grupo)
   e. Reseta task progress
   f. Executa comandos pós-promoção
   g. Registra histórico
5. JobRankMilestoneService detecta novo rank:
   a. Libera slots de profissão
   b. Profissões elegíveis ficam disponíveis
6. Jogador usa slot → /jobs entrar <id>
7. JobLicenseService verifica unlock-requirements:
   a. unlockedByDefault?
   b. Rank suficiente?
   c. Permissão necessária?
8. Se licença necessária → missão curta → licença permanente
9. Profissão ativa → ações geram XP + moedas + crate rewards
10. CrateRewardDefinition processa chance de chave:
    a. matchesAction() filtra por tipo de ação
    b. Chance roll → concede chave virtual
    c. Limites diários e cooldown respeitados
11. Jogador usa chave → /crates → abre crate → loot
```

## 6. Mapa de Dependências entre Módulos

| Módulo Origem | Módulo Destino | Tipo de Dependência | Classe/Arquivo Chave |
|--------------|----------------|---------------------|---------------------|
| RankUp | Jobs | Leitura de rank | `RankupManager` → `JobRankMilestoneService` |
| RankUp | Jobs | Verificação de rank | `RankupManager` → `JobLicenseService` (via `isAtOrAboveRank`) |
| RankUp | Jobs | Verificação de rank | `RankupManager` → `JobRewardRollService` (via `requiredRankId`) |
| Jobs | Crates | Concessão de chaves | `JobRewardRollService` → `SpecialistKeyService` |
| Jobs | Crates | Definição de recompensa | `CrateRewardDefinition` (config) |
| Jobs | Crates | Fallback legado | `JobKeyDropRule` → `KeyGiveCommand` |
| RankUp | LuckPerms | Grupo de permissão | `RankupLuckPermsSettings` |
| Jobs | LuckPerms | Permissão de profissão | `UnlockRequirements.permission` |
| Jobs | Cobblemon | Eventos Pokémon | `OptionalJobsIntegration` (6 bridges) |
| Jobs | Economy | Depósito de moedas | `JobRewardApplier` → `EconomyAPI` |
| RankUp | Economy | Withdraw de moedas | `RankupPromotionService` → `EconomyAPI` |
| RankUp | Gems | Débito de gems | `RankupPromotionService` → `GemsManager` |

## 7. Configuração Integrada (Exemplo Completo)

### 7.1 rankup.json (trecho)

```json
{
  "enabled": true,
  "ladder": {
    "id": "main",
    "display-name": "&6Progression",
    "initial-rank-id": "member",
    "luckperms-mode": "REPLACE_LADDER_INHERITANCE_AND_PRIMARY",
    "require-confirmation": true
  },
  "ranks": [
    {
      "id": "novice",
      "order": 1,
      "display-name": "&aNovice",
      "description": ["&7Rank inicial"],
      "icon": { "item": "minecraft:wooden_sword" },
      "luckperms": { "group": "novice", "set-as-primary-group": true },
      "requirements": {
        "money": 5000.0,
        "gems": 3,
        "task-mode": "ALL",
        "tasks": [
          { "id": "break_logs", "display-name": "&6Wood Collector", "type": "BREAK_BLOCK", "target": 30,
            "filters": { "blocks": ["#minecraft:logs"] }, "enabled": true }
        ]
      },
      "actions": {
        "broadcast": "&a%player% ascended to Novice!",
        "commands": ["give %player% minecraft:diamond 3"]
      },
      "enabled": true
    }
  ]
}
```

### 7.2 jobs/miner.json (com crate rewards)

```json
{
  "id": "miner",
  "enabled": true,
  "display-name": "Minerador",
  "icon": "minecraft:diamond_pickaxe",
  "category": "COMMON",
  "unlock-requirements": {
    "unlocked-by-default": true
  },
  "actions": {
    "BREAK-BLOCK": {
      "minecraft:diamond_ore": { "money": 30, "xp": 40 },
      "*": { "money": 0.5, "xp": 1 }
    }
  },
  "crate-rewards": [
    {
      "actions": ["BREAK-BLOCK"],
      "key-id": "craft_key",
      "chance": 0.005,
      "amount": 1,
      "minimum-job-level": 5,
      "daily-limit": 3,
      "cooldown-seconds": 1800
    }
  ]
}
```

## 8. Validação (JobConfigurationValidator)

Regras adicionais de validação para os campos de integração:

### crate-rewards
- `chance` fora de 0–1 → erro
- `amount` < 1 → erro
- `key-id` vazio → erro
- `minimumJobLevel` < 1 → erro
- `dailyLimit` < 0 → erro
- Action types desconhecidos → warning (não bloqueante)

### unlock-requirements
- `unlockedByDefault` define se as demais verificações são aplicadas
- `requiredRankOrder` mínimo 0 (0 = sem verificação de ordem)
- `requiredRankId` e `permission` com string vazia são tratados como null

## 9. Limites e Anti-Exploit

| Mecanismo | Descrição | Config |
|-----------|-----------|--------|
| Limite diário global | Ganho máximo total de moedas por dia | `global.json → daily-limit.global-limit` |
| Limite diário por job | Ganho máximo por profissão | `profissão → max-daily-earnings` |
| Limite diário de crate reward | Máximo de chaves por dia por recompensa | `crate-reward → daily-limit` |
| Cooldown de crate reward | Tempo mínimo entre concessões | `crate-reward → cooldown-seconds` |
| Nível mínimo de job | Profissão precisa estar no nível X | `crate-reward → minimum-job-level` |
| Rank mínimo | Jogador precisa de rank X ou superior | `crate-reward → required-rank-id` |
| Permissão | Jogador precisa da permissão LuckPerms | `unlock-requirements → permission` |
| Cooldown de slot | Tempo entre trocas de profissão | `slots.json → cooldown-minutes` |

## 10. Referência de Arquivos

### Configuração

| Arquivo | Módulo | Propósito |
|---------|--------|-----------|
| `config/bigbangessentials/rankup.json` | RankUp | Definição de ranks e ladder |
| `config/bigbangessentials/jobs/global.json` | Jobs | Configuração global (limites, AFK) |
| `config/bigbangessentials/jobs/slots.json` | Jobs | Definição de slots de profissão |
| `config/bigbangessentials/jobs/milestones.json` | Jobs | Milestones / rank → slot unlocks |
| `config/bigbangessentials/jobs/professions/*.json` | Jobs | Configuração individual de cada profissão |
| `config/crates.json` | Crates | Definições de crates |
| `config/keys.json` | Crates | Definições de chaves |
| `config/crate_locations.json` | Crates | Localizações de crates no mundo |

### Código Fonte (common/src/main/java/...)

| Classe | Pacote | Propósito |
|--------|--------|-----------|
| `RankupConfig.java` | `rankup.config` | Parse + translateColors() |
| `RankupPromotionService.java` | `rankup.service` | Promoção transacional |
| `RankupMenuSupport.java` | `rankup.menu` | GUI com cores preservadas |
| `JobsConfig.java` | `jobs.config` | Modelos: JobDefinition, GlobalConfig |
| `JobsConfigLoader.java` | `jobs.config` | Parse de JSON, incluindo crate-rewards e unlock-requirements |
| `UnlockRequirements.java` | `jobs.config` | Record de requisitos de acesso |
| `CrateRewardDefinition.java` | `jobs.rewards` | Record de recompensa de crate por ação |
| `JobRewardRollService.java` | `jobs.rewards` | Processamento de rolagem de crate rewards |
| `JobRewardLimitService.java` | `jobs.rewards` | Controle de limites diários e cooldown |
| `JobRewardApplier.java` | `jobs.pipeline` | Aplicação de recompensas no pipeline |
| `JobRankMilestoneService.java` | `jobs.progression` | Detecção de milestones baseados em rank |
| `JobLicenseService.java` | `jobs.license` | Verificação de unlock-requirements |
| `JobCommandService.java` | `jobs` | Comandos /jobs, verificação de permissão |
| `JobConfigurationValidator.java` | `jobs` | Validação de configuração |

## 11. Testes

| Teste | Localização | Cobertura |
|-------|-------------|-----------|
| `CrateRewardConfigTest.java` | `common/src/test/java/.../jobs/` | Parse de crate-rewards, validação, action matching, defaults |

## 12. Documentação Relacionada

- [docs/Wiki/RankupSystem.md](../Wiki/RankupSystem.md) — Documentação completa do RankUp
- [docs/Wiki/JobsSystem.md](../Wiki/JobsSystem.md) — Documentação completa do sistema de profissões
- [docs/Wiki/CratesSystem.md](../Wiki/CratesSystem.md) — Documentação do módulo de crates
- [docs/modules/jobs/CONFIGURATION.md](../modules/jobs/CONFIGURATION.md) — Guia de configuração de jobs
- [docs/modules/jobs/PROFESSIONS.md](../modules/jobs/PROFESSIONS.md) — Referência de todas as 17 profissões
- [docs/modules/crates/CONFIGURATION.md](../modules/crates/CONFIGURATION.md) — Guia de configuração de crates
- [docs/operations/bigbangcraft-progression-system.md](../operations/bigbangcraft-progression-system.md) — Guia de operação e deploy
- [docs/engineering/rankup-jobs-integration.md](../engineering/rankup-jobs-integration.md) — Documento de engenharia (audit/planejamento)
