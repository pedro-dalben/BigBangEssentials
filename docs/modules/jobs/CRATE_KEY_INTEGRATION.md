# Jobs ↔ Crate Key Integration

Integração nativa entre o sistema de profissões (Jobs) e crates, permitindo que jogadores obtenham chaves de crate como recompensa durante atividades normais de trabalho.

## Visão Geral

Conforme o jogador evolui sua profissão, ele passa a ter chances de receber chaves de crate durante ações válidas da profissão. O sistema de progressão por tiers é:

| Nível Mínimo | Chave | Chance Base | Limite Diário | Cooldown |
|---|---|---|---|---|
| 10 | Chave Iniciante (`iniciante`) | 2% | 3 | 1 hora |
| 25 | Chave Intermediária (`intermediaria`) | 1% | 2 | 1.5 horas |
| 50 | Chave Avançada (`avancada`) | 0.5% | 1 | 2 horas |
| 80 | Chave Lendária (`lendaria`) | 0.2% | 1 | 4 horas |

**Comportamento**: `one-reward-per-action` ativo — apenas a chave de maior tier para a qual o jogador é elegível é concedida por ação.

## Configuração (`crate-rewards`)

Cada profissão define suas recompensas de crate no array `crate-rewards` do arquivo JSON de configuração.

### Exemplo (farmer.json)

```json
{
  "id": "farmer",
  "actions": { ... },
  "crate-rewards": [
    {
      "actions": [],
      "key-id": "iniciante",
      "key-display-name": "Chave Iniciante",
      "chance": 0.02,
      "amount": 1,
      "minimum-job-level": 10,
      "daily-limit": 3,
      "cooldown-seconds": 3600,
      "priority": 10,
      "one-reward-per-action": true,
      "physical-key": false
    }
  ]
}
```

### Campos

| Campo | Tipo | Obrigatório | Padrão | Descrição |
|---|---|---|---|---|
| `actions` | `string[]` | Não | `[]` | Ações elegíveis. Array vazio = todas as ações da profissão |
| `key-id` | `string` | **Sim** | - | ID da chave no sistema de crates |
| `key-display-name` | `string` | Não | `key-id` | Nome exibido na mensagem de obtenção |
| `chance` | `double` | Não | `0.005` | Probabilidade base (0.0 a 1.0) por ação |
| `amount` | `int` | Não | `1` | Quantidade de chaves concedidas |
| `minimum-job-level` | `int` | Não | `1` | Nível mínimo da profissão |
| `required-rank-id` | `string` | Não | `null` | ID do milestone de rank necessário |
| `daily-limit` | `int` | Não | `3` | Máximo de drops diários desta chave |
| `cooldown-seconds` | `long` | Não | `1800` | Intervalo mínimo (segundos) entre drops |
| `priority` | `int` | Não | `0` | Prioridade (maior = avaliado primeiro, usado com `one-reward-per-action`) |
| `one-reward-per-action` | `boolean` | Não | `false` | Se `true`, apenas a primeira recompensa elegível é concedida |
| `physical-key` | `boolean` | Não | `false` | Se `true`, entrega chave física no inventário; `false` = chave virtual |

## Chaves de Progressão Pré-criadas

As seguintes chaves e crates são criadas automaticamente no startup:

| ID | Nome | Tier |
|---|---|---|
| `iniciante` | Chave Iniciante | 1 (Nível 10) |
| `intermediaria` | Chave Intermediária | 2 (Nível 25) |
| `avancada` | Chave Avançada | 3 (Nível 50) |
| `lendaria` | Chave Lendária | 4 (Nível 80) |
| `craft_key` | Chave do Ofício | Fragmentos |
| `ascension_key` | Chave de Ascensão | Ascensão |
| `specialist_key` | Chave de Especialista | Pokémon |

Cada chave tem uma crate correspondente (`iniciante_crate`, `intermediaria_crate`, etc.) criada automaticamente.

## Comando para Entrega Manual

```
/crate key give <jogador> <key-id> <quantidade>
```

Exemplo:
```
/crate key give Pedro iniciante 1
```

## Registro de Tentativas

Toda tentativa de drop de chave é registrada na tabela `bbe_jobs_key_rolls`:

```sql
CREATE TABLE IF NOT EXISTS bbe_jobs_key_rolls (
    roll_id        VARCHAR(64) NOT NULL,
    action_id      VARCHAR(255) NOT NULL,
    uuid           VARCHAR(36) NOT NULL,
    job_id         VARCHAR(64) NOT NULL,
    job_level      INT NOT NULL,
    base_chance    DOUBLE NOT NULL,
    action_weight  DOUBLE NOT NULL,
    final_chance   DOUBLE NOT NULL,
    random_value   DOUBLE NOT NULL,
    success        BOOLEAN NOT NULL,
    reason         VARCHAR(255),
    created_at     BIGINT NOT NULL,
    PRIMARY KEY (roll_id)
);
```

Cada ação válida dispara um roll para cada tier de chave configurado. O resultado (sucesso ou falha) é sempre registrado, permitindo auditoria completa.

## Fluxo de Processamento

1. Jogador realiza ação válida da profissão (ex: colheita, pesca, mineração)
2. Pipeline de jobs processa ação → valida → avalia regras → calcula recompensas
3. `JobRewardRollService.processActionRewards()` é chamado
4. Para cada `CrateRewardDefinition` configurado:
   - Verifica se ação é elegível (`actions` vazio = todas)
   - Verifica nível mínimo da profissão
   - Verifica milestone de rank (opcional)
   - Verifica limites (cooldown, diário por job, diário total)
   - Rola dado (RNG) com `finalChance = baseChance × actionWeight`
   - Registra resultado em `bbe_jobs_key_rolls`
   - Se sucesso: concede chave via `CrateRewardGateway`
5. Se `one-reward-per-action` = `true`, para após primeira recompensa concedida

### Ordenação por Prioridade

Recompensas são ordenadas por `priority` (decrescente) antes da avaliação. Com `one-reward-per-action`, a chave de maior tier elegível é testada primeiro (highest-tier-first).

## Mensagens

Quando uma chave é obtida, o jogador recebe:

```
§6§lSorte no Trabalho! §eVocê encontrou §l1x Chave Iniciante§l!
```

O nome exibido usa `key-display-name` da configuração (fallback: `key-id`).

## Suporte a Chaves Físicas

Para entregar chaves como itens físicos no inventário:

```json
{
  "key-id": "iniciante",
  "physical-key": true
}
```

Se o jogador estiver offline no momento do drop, o sistema faz fallback automático para chave virtual.

## Correções de Bugs (Build 1058+)

### HARVEST-CROP
- **Problema**: Configs legadas com `break-block` não geravam recompensa para colheitas maduras classificadas como `HARVEST_CROP`.
- **Solução**: `JobRuleEvaluator` agora faz fallback de `HARVEST_CROP` → `BREAK_BLOCK` para compatibilidade com configs antigas.

### EXPLORE
- **Problema**: Eventos `EXPLORE` não disparavam corretamente.
- **Solução**: O sistema usa reserva atômica (`reserve → confirm/cancel`) que só confirma descoberta após pipeline completo de validação e recompensa.

### USE-MAGIC
- **Problema**: Não havia event handler para `USE_MAGIC`.
- **Solução**: Adicionado handler que detecta interações com Mesa de Encantamento e Suporte de Poções (NeoForge: `PlayerInteractEvent.RightClickBlock`, Fabric: `UseBlockCallback`).
