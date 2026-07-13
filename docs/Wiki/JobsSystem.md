# Módulo de Trabalhos e Profissões (Jobs Ecosystem) — BigBangEssentials

O módulo **Jobs** do **BigBangEssentials** é um ecossistema avançado de profissões, progressão de carreira, missões e economia projetado para o modpack **Cobbleverse** (Fabric e NeoForge). Ele transforma atividades rotineiras do jogo e interações com Pokémon em um ciclo contínuo e engajante de recompensas, totalmente configurável via arquivos JSON/YAML e protegido por um motor anti-exploit de alta precisão.

---

## 1. Visão Geral e Arquitetura

O sistema de Jobs opera sobre uma arquitetura orientada a eventos normalizados e processamento idempotente, garantindo alta performance, consistência de dados e segurança contra falhas ou abusos.

### 1.1 Pipeline Central (`JobAction`)
Todas as atividades no jogo — desde quebrar um bloco até capturar um Pokémon ou vencer uma Raid — são encapsuladas no objeto imutável **`JobAction`**:
* **`JobActionType`**: Normaliza os tipos de ação (ex: `BREAK_BLOCK`, `FISH`, `POKEMON_CAPTURED`, `EGG_HATCHED`, `RAID_CLEARED`, `TRAINER_BATTLE_WON`, `FOSSIL_REVIVED`, `PASTURE_TASK_COMPLETED`).
* **`JobActionContext`**: Carrega metadados contextuais, como coordenadas, dimensão, tags de bloco/item, se foi colocado por jogador (`playerPlacedBlock`), origem do evento e atributos personalizados (`admin_spawned`, `is_traded`, `is_bred`).
* **ID Idempotente (`actionId`)**: Cada ação possui um UUID exclusivo gerado na origem do evento. O banco de dados (SQLite/MySQL) através do `JobActionReceiptRepository` impede duplicidade de processamento, garantindo que o jogador nunca receba recompensas duplicadas por falhas de rede ou travamentos de servidor.

### 1.2 Tolerância a Falhas via Reflexão (Cobbleverse Bridges)
O sistema integra-se de forma nativa a mods externos (como Cobblemon e complementos do Cobbleverse) sem gerar dependências rígidas no classpath:
* **`OptionalJobsIntegration`**: Interface que gerencia o ciclo de vida de cada bridge (`CobblemonJobsBridge`, `BreedingJobsBridge`, `TrainerJobsBridge`, `RaidDensJobsBridge`, `FossilJobsBridge`, `PastureJobsBridge`).
* **Sem Polling ou Sniffing**: A verificação de eventos externos ocorre exclusivamente por reflexão segura de classes de evento presenciais na inicialização do servidor. Não utilizamos polling em loop, varredura de chat, análise de pacotes ou heurísticas frágeis de inventário.
* **Resiliência**: Se um mod de Raid ou Breeding for removido ou desativado, o bridge transita automaticamente para os estados `DISABLED_MISSING_API` ou `DEGRADED`, emitindo logs de auditoria sem impactar as demais profissões ou causar crash no servidor.

---

## 2. Profissões Disponíveis

O ecossistema conta com 17 profissões padrão divididas entre atividades de sobrevivência/construção (Vanilla) e especializações avançadas no universo Pokémon (Cobbleverse).

### 2.1 Profissões Vanilla
1. **Minerador (`miner`)**: Coleta de minérios, pedras preciosas e exploração subterrânea.
2. **Lenhador (`woodcutter`)**: Corte de árvores, madeiras raras e desmatamento planejado.
3. **Agricultor (`farmer`)**: Colheita de plantações maduras, cultivo e botânica.
4. **Construtor (`builder`)**: Posicionamento de blocos arquitetônicos e construção civil.
5. **Ferreiro (`blacksmith`)**: Fundição de ferros, ligas metálicas e reparação de ferramentas.
6. **Artesão (`crafter`)**: Criação de itens de bancada, ferramentas avançadas e utilitários.
7. **Explorador (`explorer`)**: Descoberta de novos biomas e estruturas pelo mundo.
8. **Caçador/Ranger (`ranger`)**: Abate de monstros hostis e controle de ameaças.
9. **Culinário (`culinarian`)**: Preparo de refeições, assados e consumíveis especiais.
10. **Mago (`magician`)**: Encantamentos, alquimia e uso de artefatos mágicos.
11. **Pescador (`fisherman`)**: Pesca de peixes, tesouros aquáticos e recompensas marinhas.

### 2.2 Profissões Pokémon (Cobbleverse)
As profissões Pokémon são voltadas para mecânicas específicas do ecossistema Cobblemon e requerem progressão de carreira para serem desbloqueadas:
1. **Pesquisador Pokémon (`researcher`)**: Captura de Pokémon selvagens e registro de novas espécies na Pokédex (`POKEMON_CAPTURED`, `DEX_ENTRY_ADDED`).
2. **Criador Pokémon (`breeder`)**: Cuidado com ovos Pokémon, incubação e cruzamento genético bem-sucedido (`EGG_CREATED`, `EGG_HATCHED`).
3. **Especialista em Raids (`raider`)**: Batalhas e vitórias contra chefes poderosos em Raid Dens (`RAID_CLEARED`).
4. **Treinador da Liga (`trainer`)**: Vitórias em batalhas contra treinadores NPC, líderes de ginásio e desafiantes (`TRAINER_BATTLE_WON`).
5. **Paleontólogo (`paleontologist`)**: Escavação e revivescência de fósseis Pokémon pré-históricos (`FOSSIL_REVIVED`).
6. **Cuidador de Pasture (`pasture_keeper`)**: Gestão de habitats e conclusão de tarefas e coletas ativas em Pastures (`PASTURE_TASK_COMPLETED`).

---

## 3. Progressão de Carreira: RankUp, Licenças e Slots

A progressão do jogador segue uma jornada interligada ao sistema global do servidor:

```
RankUp Alcançado (Starter -> Specialist -> Researcher)
       ↓
Novo Slot de Profissão Liberado
       ↓
Solicitação de Licença da Profissão
       ↓
Conclusão da Missão Curta de Licenciamento
       ↓
Profissão Ativa com Ganhos e XP Liberados
```

### 3.1 Marcos de RankUp e Slots de Profissão (`JobSlotDefinition`)
Os jogadores não podem ativar todas as profissões simultaneamente; eles possuem **Slots de Profissão** que são desbloqueados conforme avançam no sistema de RankUp:
* **Marco `starter` (Rank Inicial)**: Desbloqueia o slot `COMMON_PRIMARY` (permite escolher 1 profissão básica de sobrevivência, como Minerador ou Lenhador).
* **Marco `specialist` (Rank Especialista)**: Desbloqueia o slot `COMMON_SECONDARY` (permite escolher uma 2ª profissão de suporte ou manufatura, como Construtor, Ferreiro ou Artesão).
* **Marco `researcher` (Rank Pesquisador)**: Desbloqueia o slot `POKEMON_SPECIALIZATION` (permite escolher 1 especialização Pokémon avançada, como Pesquisador, Criador ou Especialista em Raids).

### 3.2 Sistema de Licenças e Missões Curtas (`JobLicenseService`)
Para ativar uma profissão em um slot livre, o jogador deve primeiro obter a **Licença da Profissão**:
1. Ao ingressar, a profissão entra em estado de **Licença em Progresso** (`InProgressLicense`).
2. O jogador deve cumprir uma missão curta e dinâmica de demonstração de habilidade (configurada em `license-objectives` no JSON do trabalho).
   * *Exemplo Pesquisador*: Capturar 5 Pokémon selvagens válidos.
   * *Exemplo Criador*: Chocar 3 ovos Pokémon.
   * *Exemplo Minerador*: Quebrar 25 minérios de pedra ou carvão.
3. Ao completar a missão, a licença torna-se **Permanente** (`PermanentLicense`), liberando os ganhos em moedas, XP e pontos de habilidade.

---

## 4. Ciclo de Recompensas e Economia

Cada ação válida executada em uma profissão ativa alimenta um ciclo completo de economia e recompensas:

```
Ação Válida Executada
       ↓
Moedas (Economia Global) + XP da Profissão
       ↓
Fragmentos de Jornada (Garantidos/Previsíveis)
       ↓
Roll de Sorte: Chance Rara de Chave do Ofício ou Chave de Especialista
       ↓
Abertura de Crates (Caixa do Ofício / Caixa de Especialista / Caixa de Ascensão)
       ↓
Itens, Materiais, Utilitários e Cosméticos (Zero P2W)
```

### 4.1 Moedas, XP e Habilidades
* **Moedas**: Depositadas na conta global do jogador (compatível com Vault/Economia padrão). Cada profissão possui um **Limite Diário de Ganhos** (`max-daily-earnings`), evitando desequilíbrios inflacionários.
* **XP e Níveis**: Subir de nível aumenta multiplicadores de ganho em moedas e concede **Pontos de Habilidade** (`skill-points`), que podem ser gastos em árvores de habilidades passivas exclusivas da profissão (ex: *Pesquisa de Campo Avançada*, *Incubação Lucrativa*).

### 4.2 Fragmentos de Jornada e Chaves de Crates
Para eliminar a frustração do RNG puro, o sistema utiliza uma economia híbrida:
* **Fragmentos de Jornada**: Concedidos de forma previsível ao realizar ações válidas e completar missões/contratos. Podem ser acumulados e trocados diretamente por chaves de Crates.
* **Roll de Sorte (`ACTION_WEIGHT_ROLL` / `JOB_LUCK`)**: Cada ação executada possui uma chance percentual (rolagem ponderada) de conceder diretamente uma **Chave do Ofício** ou **Chave de Especialista**.
* **Crates de Profissão**: As chaves abrem a *Caixa do Ofício*, *Caixa de Especialista* ou *Caixa de Ascensão*, que recompensam com materiais de craft, blocos especiais, ferramentas encantadas e cosméticos.

> [!IMPORTANT]
> **Política Anti-P2W (Pay-to-Win):** O gateway de recompensas de crates (`DefaultCrateRewardGateway` e `SpecialistKeyService`) veta estritamente a injeção de Pokémon Lendários, Pokémon Shinies ou vantagens competitivas desleais dentro de Crates de Profissão. As recompensas são restritas a cosméticos, utilitários, recursos de progressão de carreira e economia balanceada.

### 4.3 Contratos Diários e Semanais (`JobContractService`)
Os jogadores podem aceitar **Contratos de Trabalho** diários e semanais para complementar sua renda. Os contratos oferecem metas alternativas (ex: entregar 50 minérios refinados ou concluir 10 tarefas no Pasture) em troca de grandes quantias de moedas, XP e Fragmentos de Jornada garantidos.

---

## 5. Segurança e Proteção Anti-Exploit

O módulo possui uma camada central de validação (`JobActionValidator` e `PokemonJobActionValidator`) que intercepta tentativas de exploração (exploits) antes de qualquer processamento de recompensa:

1. **Blocos Colocados por Jogadores (`playerPlacedBlock`)**: O sistema rastreia blocos colocados por jogadores (mesmo após reinicializações do servidor). Quebrar um minério ou madeira colocada por você ou outro jogador **não** concede XP, moedas ou fragmentos.
2. **Origem Artificial em Pokémon (`admin_spawned`, `command`)**: Pokémon gerados por comandos de GM/Admin, blocos de comando ou spawners artificiais são identificados e bloqueados.
3. **Trades e Ovos (`is_traded`, `is_bred`)**: Capturar um Pokémon que foi recebido via troca com outro jogador ou que acabou de nascer de um ovo **não** aciona recompensas da profissão de *Pesquisador*, evitando o farm infinito entre contas.
4. **Spam de Mesma Espécie**: Capturar repetidamente a mesma espécie de Pokémon em intervalos inferiores a 3 segundos aciona o bloqueio de spam anti-macro.
5. **Farm Passivo em Pastures**: Tarefas ou entregas no Pasture que não originem de uma interação manual legítima do jogador (`manual` ou `contract_delivery`) são rejeitadas como farm passivo.
6. **Auditoria (`PokemonJobAuditService`)**: Todas as ações suspeitas rejeitadas são registradas nos logs de auditoria do servidor com timestamp, UUID do jogador, razão e detalhes do alvo, permitindo inspeção via comandos administrativos.

---

## 6. Menus (GUI) e Integrações

A interface do usuário é gerenciada através de menus interativos altamente customizáveis na pasta `config/bigbangessentials/menus/`:
* **Menu Principal de Profissões (`jobs_menu.yml`)**: Exibe todas as profissões disponíveis, divididas por categoria (Comum, Manufatura, Especialização Pokémon), indicando status de licença, nível atual e slots disponíveis.
* **Menu de Detalhes e Habilidades (`job_details_menu.yml`)**: Permite visualizar o progresso da licença em andamento, aceitar missões curtas, gastar pontos de habilidade na árvore passiva e consultar contratos ativos.
* **Ações Integradas**: Suporte a placeholders dinâmicos e ações de clique rápidas, como `toggle_job`, `open_job_details`, `claim_license_reward` e integração com o menu de teleporte e Crates.

---

## 7. Referência de Comandos e Permissões

### 7.1 Comandos para Jogadores (`/jobs`)
| Comando | Descrição | Permissão |
| :--- | :--- | :--- |
| `/jobs` ou `/jobs menu` | Abre o menu principal interativo de profissões. | Padrão (Livre) |
| `/jobs list` | Lista no chat todas as profissões disponíveis no servidor. | Padrão (Livre) |
| `/jobs entrar <id>` | Inicia a licença ou ingressa na profissão especificada. | `jobs.profissao.<id>` |
| `/jobs sair <id>` | Abandona uma profissão ativa (respeitando cooldowns). | Padrão (Livre) |
| `/jobs info <id>` | Exibe estatísticas detalhadas, nível, XP e habilidades de uma profissão. | Padrão (Livre) |
| `/jobs licenca <id>` | Exibe o progresso atual da missão de licenciamento. | Padrão (Livre) |
| `/jobs contratos` | Abre a tela de contratos diários e semanais disponíveis. | Padrão (Livre) |

### 7.2 Comandos Administrativos (`/jobs admin`)
| Comando | Descrição | Permissão |
| :--- | :--- | :--- |
| `/jobs admin reload` | Recarrega todas as configurações JSON/YAML, menus e regras. | `jobs.admin` |
| `/jobs admin integrations` | Exibe o status de saúde em tempo real de todas as 6 bridges do Cobbleverse. | `jobs.admin` |
| `/jobs admin audit [player]` | Exibe os últimos eventos de exploração rejeitados pelo anti-exploit. | `jobs.admin` |
| `/jobs admin pokemon status <player>` | Exibe os cooldowns e timers de captura/batalha de um jogador. | `jobs.admin` |
| `/jobs admin pokemon grantkey <player> <qtd>`| Concede diretamente Chaves de Especialista para um jogador. | `jobs.admin` |
| `/jobs admin pokemon resetcd <player>` | Zera todos os tempos de recarga e bloqueios anti-spam de um jogador. | `jobs.admin` |
| `/jobs admin migrate` | Executa rotinas de migração e converte dados de sistemas legados. | `jobs.admin` |

---

## 8. Guia de Configuração (YAML / JSON)

### 8.1 Configuração Global (`world/serverconfig/bigbangessentials/jobs/global.json`)
O arquivo global controla os limites de slots e a economia diária do servidor:
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

### 8.2 Configuração de Trabalho Individual (`world/serverconfig/bigbangessentials/jobs/professions/<id>.json`)
Cada profissão possui um arquivo de configuração próprio em formato JSON. O motor de regras suporta **alvos exatos**, **grupos por tag** (`#pokemon`, `#ores`) e **curingas universais** (`*` ou `any`).

*Exemplo completo de configuração avançada (`researcher.json`):*
```json
{
  "id": "researcher",
  "enabled": true,
  "display-name": "Pesquisador Pokémon",
  "description": "Ganhe XP e moedas capturando Pokémon e registrando novas espécies na Pokédex.",
  "permission": "jobs.profissao.researcher",
  "unlocked-by-default": false,
  "category": "POKEMON_SPECIALIZATION",
  "license-required": true,
  "max-level": 100,
  "max-daily-earnings": 15000.0,
  "money-bonus-per-level": 1.0,
  "max-level-money-bonus": 100.0,
  "xp-curve": {
    "initial-xp": 150,
    "multiplier": 1.25
  },
  "skill-point-settings": {
    "every": 2
  },
  "actions": {
    "POKEMON_CAPTURED": {
      "mewtwo": { "money": 500.0, "xp": 1000.0 },
      "rayquaza": { "money": 500.0, "xp": 1000.0 },
      "#rare_pokemon": { "money": 50.0, "xp": 80.0 },
      "*": { "money": 15.0, "xp": 20.0 }
    },
    "DEX_ENTRY_ADDED": {
      "*": { "money": 50.0, "xp": 75.0 }
    }
  },
  "license-objectives": [
    {
      "id": "lic_res_capture",
      "action": "POKEMON_CAPTURED",
      "amount": 5,
      "message": "Capture 5 Pokémon para demonstrar suas habilidades de campo."
    }
  ],
  "crate-rewards": [
    {
      "actions": ["POKEMON-CAPTURED"],
      "key-id": "researcher_key",
      "chance": 0.02,
      "amount": 1,
      "minimum-job-level": 5,
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
    }
  ],
  "unlock-requirements": {
    "unlocked-by-default": false,
    "required-rank-id": "adept",
    "required-rank-order": 3,
    "permission": "jobs.profissao.researcher"
  },
  "skills": {
    "pesquisa_avancada": {
      "name": "Pesquisa de Campo Avançada",
      "description": "+3% de XP por rank ao capturar ou registrar Pokémon.",
      "max-rank": 5,
      "point-cost": 1,
      "required-level": 5,
      "prerequisites": [],
      "effects": {
        "xp-multiplier": 0.03
      }
    }
  },
  "crate-rewards": [
    {
      "actions": ["POKEMON-CAPTURED"],
      "key-id": "researcher_key",
      "chance": 0.02,
      "amount": 1,
      "minimum-job-level": 5,
      "daily-limit": 5,
      "cooldown-seconds": 3600
    }
  ],
  "unlock-requirements": {
    "unlocked-by-default": false,
    "required-rank-id": "adept",
    "required-rank-order": 3,
    "permission": "jobs.profissao.researcher"
  },
  "level-up-rewards": {
    "10": { "commands": ["give %player% cobblemon:poke_ball 16"] },
    "50": { "commands": ["give %player% cobblemon:ultra_ball 32"] }
  },
  "messages": {
    "level-up": "§aVocê alcançou o nível %level% como Pesquisador Pokémon! Pontos: +%points%"
  }
}
```

#### Hierarquia de Avaliação de Recompensas (`actions`)
Quando um jogador executa uma ação (ex: capturar o Pokémon `mewtwo`), o motor `JobRuleEvaluator` consulta o bloco `actions` na seguinte ordem de prioridade:
1. **Correspondência Exata**: Verifica se existe a chave `"mewtwo"`. Como existe, concede `500.0` moedas e `1000.0` XP.
2. **Correspondência por Tag**: Se não houver chave exata (ex: capturar um `dragonite`), verifica se o alvo pertence a alguma tag listada (ex: `"#rare_pokemon"`).
3. **Curinga Universal (`*` ou `any`)**: Se não for um alvo exato nem pertencer a uma tag específica (ex: capturar um `caterpie`), o motor aplica automaticamente a recompensa padrão definida no curinga `"*"`, concedendo `15.0` moedas e `20.0` XP.

Esse mecanismo garante máxima flexibilidade para os administradores do servidor sem sobrecarregar os arquivos de configuração.
