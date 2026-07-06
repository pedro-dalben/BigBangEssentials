# 📘 Progression System & Operations Guide: BigBangCraft

Este documento serve como o manual definitivo de configuração, operação, deploy, monitoramento e rollback para o ecossistema integrado do servidor **BigBangCraft**.

---

## 1. Visão Geral do Sistema

O fluxo de progressão do jogador no BigBangCraft é cíclico e interligado através dos seguintes passos:

```text
RankUp (LuckPerms & DB)
  │
  ├──► [Auto Sync] Milestone de Carreira (JobsConfig)
  │                   │
  │                   ├──► Libera Licenças de Jobs (/jobs)
  │                   └──► Libera Slots de Job Ativos
  │
  └──► [Ações Ativas em Jobs]
          │
          ├──► Ganho de XP e Coins (Limite diário, anti-exploit e anti-AFK)
          │
          ├──► Acúmulo de Fragmentos de Jornada (Fórmula por Peso de Ação)
          │       │
          │       └──► Conversão Atômica (12 Fragmentos ➔ 1 Chave do Ofício)
          │
          ├──► Chance de Chave Virtual (Fórmula de Sorte baseada em nível)
          │
          ├──► Progresso de Contratos (Diários / Semanais / Especialização)
          │       │
          │       └──► Resgate de Recompensas (Coins, XP, Fragmentos, Chaves)
          │
          └──► Especializações Pokémon (Capture, Hatch, Raid, Battles)
                  │
                  └──► Caixa de Especialista & Recompensas de Elite
```

---

## 2. Arquitetura e Matriz de Responsabilidade

Para evitar duplicação ou descompasso de dados, cada módulo atua como a única **Fonte da Verdade** para suas respectivas responsabilidades:

| Módulo | Fonte de Verdade | O que controla | O que NÃO controla |
| :--- | :--- | :--- | :--- |
| **BigBangRankUp** | Ranks e Transições | Ranks do jogador, custos de promoção, ordem das patentes, sincronização LuckPerms e comandos pós-promoção. | Níveis de carreira, XP de Jobs, slots ativos ou licenças. |
| **BigBangEssentials Jobs** | Carreira e Licenciamento | Progresso de Jobs, XP, moedas de trabalho, licenças de job concluídas, liberação de slots ativos por milestones. | Alteração de rank primário ou permissões do chat. |
| **Crates** | Recompensas virtuais | Chaves de Crates, sorteios probabilísticos de itens, entregas pendentes em caso de inventário cheio. | Progressão direta de rank ou níveis de job. |
| **Contratos** | Metas Temporárias | Geração de metas diárias/semanais baseadas em ações do jogador, controle de resgates idempotentes. | Ordem dos Ranks ou resultados do roll da Crate. |
| **Integração Cobbleverse** | Capturas e Eventos Pokémon | Detecção de capturas reais, batalhas ganhas contra NPCs, hatches de ovos, e deduplicação de ações abusivas. | Balanço financeiro final ou permissão direta de Rank. |

---

## 3. Pré-requisitos de Instalação

* **Minecraft:** `1.21.1`
* **NeoForge:** `21.1.179` ou **Fabric Loader:** `>=0.16.9` (com **Fabric API** `0.102.0+1.21.1`)
* **Mod Principal:** `BigBangEssentials-1.0.2.6.jar`
* **Dependências Recomendadas:** 
  * `LuckPerms` (Para gestão de permissões e grupos de Rank)
  * `Cobblemon` (Para suporte ao módulo de Especializações Pokémon)
* **Storage Recomendado:** 
  * Desenvolvimento local: `SQLITE` (Arquivo padrão: `bigbangessentials/database/bigbangessentials.db`)
  * Produção: `MYSQL` (Conexão HikariCP em banco externo dedicado)

---

## 4. Ordem Correta de Configuração

Para instalar e ativar o sistema do zero em produção, siga exatamente estes passos:

1. **Configurar o Banco de Dados:** Configure o tipo (`SQLITE` ou `MYSQL`) no arquivo `world/serverconfig/bigbangessentials/database.json`.
2. **Definir Ranks e Transições:** Configure as patentes e requisitos no `world/serverconfig/bigbangessentials/rankup.json`.
3. **Sincronizar Permissões:** Defina os grupos no LuckPerms correspondentes a cada Rank.
4. **Registrar Milestones de Carreira:** Vincule quais patentes liberam slots e profissões extras.
5. **Ajustar Profissões e Recompensas:** Habilite as profissões em `world/serverconfig/bigbangessentials/jobs/` com seus ganhos por bloco/ação.
6. **Definir Conversões:** Configure as regras de fragmentos, limites diários de ganhos de Job, e taxas de troca de chaves.
7. **Cadastrar Crates virtuais:** Configure as tabelas de drop das crates (`craft_crate`, `ascension_crate`, `specialist_crate`).
8. **Desenhar Templates de Contratos:** Desenhe as pools de missões diárias e semanais.
9. **Ativar Integração Pokémon:** Suba o mod Cobblemon e valide o carregamento dinâmico da bridge via reflexão.
10. **Rodar Validação de Comandos:** Atribua permissões aos grupos usando o LuckPerms.

---

## 5. Configuração de Ranks

Os Ranks são cadastrados em `world/serverconfig/bigbangessentials/rankup.json`. Cada Rank possui um número de ordem sequencial (`order`), requisitos financeiros ou de tarefas, e ações que executam após a promoção.

### Exemplo de Configuração de Rank:
```json
{
  "id": "iron",
  "order": 1,
  "displayName": "&fRank Iron",
  "enabled": true,
  "luckPerms": {
    "group": "iron",
    "primary": true
  },
  "requirements": {
    "money": 10000.0,
    "gems": 5,
    "tasks": [
      {
        "id": "break_iron_ore",
        "displayName": "Minerar Ferro",
        "actionType": "BREAK_BLOCK",
        "targetId": "minecraft:iron_ore",
        "amount": 50
      }
    ]
  },
  "actions": {
    "broadcast": "&a&l[Progresso] &fO jogador %player% subiu para o &fRank Iron!",
    "commands": [
      "give %player% iron_ingot 16"
    ]
  }
}
```

### Comandos de Administração de Rank:
* `/bigbangessentials rankup reload` — Recarrega as configurações.
* `/bigbangessentials rankup set <jogador> <rank_id>` — Altera administrativamente o rank de um jogador (Sincroniza os milestones de Jobs correspondentes automaticamente).
* `/bigbangessentials rankup reset <jogador>` — Reseta o progresso de tarefas do rank atual do jogador.

---

## 6. Configuração de Milestones, Jobs e Slots

As profissões e slots extras são liberados através de **Milestones**.

| Milestone | Rank Requerido | Slots Liberados | Jobs Elegíveis |
| :--- | :--- | :---: | :--- |
| `starter_milestone` | `member` | 1 | miner, woodcutter, farmer |
| `veteran_milestone` | `iron` | 2 | miner, woodcutter, farmer, builder, ranger |
| `specialist_milestone`| `diamond` | 3 | Todos + Especializações Pokémon |

---

## 7. Configuração de Jobs

Cada Job cadastrado no sistema (por exemplo, `miner.json`, `woodcutter.json`) determina os multiplicadores de ganhos.

### Exemplo de Definição de Ações:
```json
{
  "id": "miner",
  "name": "Minerador",
  "category": "COMMON",
  "maxDailyEarnings": 5000.0,
  "actions": {
    "BREAK_BLOCK": {
      "minecraft:coal_ore": {
        "money": 2.5,
        "xp": 1.0
      },
      "minecraft:diamond_ore": {
        "money": 25.0,
        "xp": 10.0
      }
    }
  }
}
```

---

## 8. Configuração de Fragmentos e Chaves

* **Taxa de Conversão:** Padrão de `12` Fragmentos de Jornada (`journey_fragments`) para `1` Chave do Ofício (`craft_key`).
* **Operação de Troca:** A troca é executada no backend de forma atômica por meio de `FragmentExchangeService`. Caso a entrega da chave falhe, os fragmentos gastos são reembolsados imediatamente (rollback transacional).
* **Comando de Troca:** `/jobs exchange <quantidade_chaves>`

---

## 9. Configuração de Crates

As crates virtuais são gerenciadas e armazenadas em banco de dados (`SQLITE` / `MYSQL`).

### Chaves e Crates Reais Disponíveis:
* **Caixa do Ofício (`craft_crate`):** Aberta com `craft_key`, obtida na troca de fragmentos de trabalho.
* **Caixa de Ascensão (`ascension_crate`):** Aberta com `ascension_key`, obtida ao subir de Rank.
* **Caixa de Especialista (`specialist_crate`):** Aberta com `specialist_key`, obtida nas Especializações Pokémon.

### Tratamento de Falhas (Inventário Cheio / Quedas):
Se o inventário do jogador estiver cheio no momento de resgatar o prêmio de uma Crate ou Contrato, a entrega física é suspensa. A recompensa é convertida em um registro pendente em banco de dados. O jogador poderá resgatá-la posteriormente usando o comando `/crates claim`.

---

## 10. Configuração de Recompensas de Rank (Ascensão)

As recompensas de Ascensão são concedidas no ato do RankUp. A configuração deve usar o schema json adequado no campo de ações de cada rank.

### Exemplo de Recompensa Garantida em Rank:
```json
"actions": {
  "commands": [
    "crates givekey %player% ascension_key 1"
  ]
}
```
*Promoções administrativas executadas via `/bigbangessentials rankup set` não disparam as recompensas de transição por padrão, evitando abusos ou duplicações indevidas.*

---

## 11. Configuração de Contratos

Os contratos gerados via `JobContractGenerator` podem ser diários (resete às 00:00 no fuso de São Paulo) ou semanais.

### Estrutura de Resgate de Contrato:
```json
{
  "templateId": "mine_coal_daily",
  "periodType": "DAILY",
  "objective": {
    "actionType": "BREAK_BLOCK",
    "targetId": "minecraft:coal_ore",
    "amount": 100
  },
  "rewards": {
    "coins": 250.0,
    "experience": 100,
    "journeyFragments": 3,
    "virtualKeyId": "craft_key",
    "virtualKeyAmount": 1
  }
}
```

---

## 12. Configuração de Cobbleverse e Especializações

Se o mod `Cobblemon` estiver ativo no servidor, a bridge dinâmica (`ReflectionCobblemonBridge`) captura e valida eventos.

### Eventos Validados e Proteções Contra Abuse:
* **`POKEMON_CAPTURED`:** Ignora pokémons gerados por comandos administrativos, trocados (`is_traded`), e previne spam aplicando um cooldown de 3 segundos por mesma espécie.
* **`TRAINER_BATTLE_WON`:** Ignora PvP comum (apenas lutas contra NPCs elegíveis dão progresso/recompensa) com cooldown de 5 segundos.
* **`RAID_CLEARED`:** Requer contribuição mínima comprovada na raid. Cooldown de 10 segundos.
* **`PASTURE_TASK_COMPLETED`:** Bloqueia farms automáticos em background. Apenas coletas manuais progridem.
* **`EGG_HATCHED`:** Deduplica ovos e rejeita registros inválidos.

---

## 13. Permissões e Comandos

### Permissões Principais:
| Permissão | Grupo | O que permite | Risco |
| :--- | :--- | :--- | :--- |
| `bigbangessentials.rankup` | Jogador | Subir de rank usando `/rankup` | Baixo |
| `bigbangessentials.jobs.use` | Jogador | Acessar menus de jobs e escolher trabalhos | Baixo |
| `bigbangessentials.crates.open`| Jogador | Abrir crates com chaves virtuais | Médio |
| `bigbangessentials.admin.rankup`| Staff | Alterar patentes de jogadores | Alto |
| `bigbangessentials.admin.jobs` | Staff | Alterar XP e moedas de jobs de jogadores | Alto |
| `bigbangessentials.admin.crates` | Staff | Dar chaves e alterar inventários virtuais | Alto |

### Comandos Operacionais:
* `/rankup` — Inicia o fluxo de promoção de patente.
* `/jobs` — Abre a interface visual de profissões.
* `/jobs exchange <qtd>` — Converte fragmentos em Chaves do Ofício.
* `/crates` — Abre a interface de crates virtuais.
* `/crates claim` — Resgata prêmios pendentes por inventário cheio.

---

## 14. Checklist de Deploy

- [ ] Certificar que o servidor de banco de dados (`SQLite` ou `MySQL`) está ativo e acessível.
- [ ] Subir as JARs do mod (`BigBangEssentials`) para a pasta `mods/`.
- [ ] Validar a integridade das configurações JSON (`rankup.json` e `database.json`) no diretório `world/serverconfig/bigbangessentials/`.
- [ ] Configurar os cargos iniciais e heranças de grupos no LuckPerms.
- [ ] Lançar o servidor de Staging e validar o bootstrap sem erros no console.
- [ ] Executar testes de jornada ponta-a-ponta com um jogador de testes (de `member` a `iron`).
- [ ] Validar que quedas e inventário cheio geram prêmios no `/crates claim`.

---

## 15. Rollback e Recuperação

1. **Parar o Servidor:** Mude o estado do servidor para offline imediatamente.
2. **Restaurar Backup do Banco:**
   * SQLite: Substituir `bigbangessentials/database/bigbangessentials.db` pelo backup correspondente.
   * MySQL: Executar rollback do dump da transação corrompida.
3. **Reverter Arquivos JSON:** Restaurar backups automáticos dos arquivos `.bak` das configurações.
4. **Subir em Modo de Manutenção:** Impedir conexões de jogadores normais para auditoria inicial.

---

## 16. Kill Switches (Operação de Emergência)

Caso ocorra um exploit em produção, desative funções imediatamente sem reiniciar o servidor:

* **Desativar conversão de Fragmentos:** Altere `getExchangeRate` ou use o comando administrativo temporário `/jobs disable-exchange`.
* **Desativar Crates Específicas:** Remova a permissão ou use `/crates disable <crate_id>`.
* **Degradar Cobbleverse:** Caso ocorra lag na bridge do Cobblemon, execute `/jobs bridge disable` para descarregar o listener dinâmico com segurança.
