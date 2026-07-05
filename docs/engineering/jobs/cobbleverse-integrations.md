# Especificação Técnica de Integrações Cobbleverse (Jobs — Fase 5)

Este documento registra a análise técnica de compatibilidade, o modelo de isolamento de bridges e o status operacional das integrações de mods para o servidor **BigBangCraft** (modpack **Cobbleverse** em Fabric 1.21.1).

---

## 1. Princípios Fundamentais de Integração

Em conformidade com as regras de segurança do **BigBangEssentials**:
1. **Zero Suposição de APIs**: Nenhuma bridge assume a presença de eventos ou classes em tempo de compilação sem verificação prévia em runtime via `Platform.isModLoaded(modId)` e reflexão segura (`Class.forName`).
2. **Zero Dependência Obrigatória**: A compilação e a inicialização do mod **não dependem** de JARs externos de Pokémon no classpath de build. Se um mod estiver ausente, a bridge correspondente é desativada silenciosamente ou marcada como `DISABLED_NOT_INSTALLED`, sem causar `ClassNotFoundException` ou `NoClassDefFoundError`.
3. **Isolamento de Falha**: Se uma bridge falhar ao inicializar ou durante o processamento de um evento (ex: incompatibilidade de versão ou API alterada em atualização do modpack), o estado da integração é alterado para `DEGRADED` ou `ERROR`. Os Jobs Vanilla (Minerador, Lenhador, Agricultor, etc.) continuam operando 100% sem interrupções.
4. **Anti-Exploit e Idempotência**: Nenhuma bridge concede moedas, XP ou chaves diretamente. Toda ação é transformada em um `JobAction` normalizado com identificador único de deduplicação e contexto validado, sendo submetida ao pipeline central `JobRewardApplier`.

---

## 2. Diagnóstico dos Mods e Status de Integração

Abaixo está o mapeamento técnico das integrações opcionais suportadas pelo sistema de Jobs:

| Integração | Mod IDs Procurados | Versão Alvo | Ações Normalizadas Suportadas | Estado Padrão (Sem Mod) | Método de Captura de Eventos |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Cobblemon Base** | `cobblemon` | 1.5.x - 1.7.x | `POKEMON_CAPTURED`, `DEX_ENTRY_ADDED` | `DISABLED_NOT_INSTALLED` | `CobblemonEvents.subscribe` (`PokemonCapturedEvent`) via reflexão |
| **Batalhas & Treinadores** | `cobblemon`, `rctmod`, `cobblemon_trainers` | 1.5.x+ | `TRAINER_BATTLE_WON` | `DISABLED_NOT_INSTALLED` | `CobblemonEvents.subscribe` (`BattleVictoryEvent`) + inspeção do oponente |
| **Breeding & Ovos** | `cobblemon`, `cobbreeding` | 1.5.x+ | `EGG_CREATED`, `EGG_HATCHED` | `DISABLED_NOT_INSTALLED` | `CobblemonEvents.subscribe` (`EggHatchEvent` / `BreedEvent`) |
| **Pastures & Manejo** | `cobblemon`, `cobblemon_pasture` | 1.5.x+ | `PASTURE_TASK_COMPLETED` | `DISABLED_NOT_INSTALLED` | Coleta manual validada e verificação sob gatilho (sem polling/tick) |
| **Paleontologia** | `cobblemon`, `cobblemon_fossils` | 1.5.x+ | `FOSSIL_REVIVED` | `DISABLED_NOT_INSTALLED` | Evento de revivificação ou extração de estação arqueológica |
| **Raid Dens** | `raiddens`, `cobblemon_raids`, `cobbleradiant` | 1.0.x+ | `RAID_CLEARED` | `DISABLED_NOT_INSTALLED` | Evento de conclusão de raid (`RaidClearedEvent` / `RaidVictoryEvent`) |

---

## 3. Detalhamento Técnico das Bridges

### 3.1 Cobblemon Base (Pesquisador Pokémon)
* **Mod ID:** `cobblemon`
* **API / Evento:** `com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent`
* **Contexto Extraído:** UUID do Pokémon, espécie, tipos, flag de shiny, flag de lendário, método de captura, bola usada e bioma.
* **Correlação Captura x Pokédex:** Para evitar dupla recompensa indevida, quando uma captura resulta no desbloqueio de uma nova entrada na Pokédex do jogador, o sistema emite `POKEMON_CAPTURED` com a recompensa base de captura e `DEX_ENTRY_ADDED` com o bônus de descoberta, vinculando ambos pelo `capture_session_id` e UUID do Pokémon no mesmo tick de processamento.
* **Anti-Exploit:** Rejeição automática de Pokémon sem UUID confiável, gerados por comandos administrativos (`/spawn`, `/give`), trocas entre jogadores ou capturas repetidas da mesma espécie em curta janela de tempo.

### 3.2 Treinadores da Liga (Radical Cobblemon Trainers / Batalhas NPC)
* **Mod ID:** `cobblemon`, `rctmod`
* **API / Evento:** `com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent`
* **Contexto Extraído:** ID da batalha, oponente derrotado, tipo de treinador (`TRAINER_COMMON`, `GYM_LEADER`, `ELITE_FOUR`, `CHAMPION`), tempo de batalha e snapshot do time.
* **Anti-Exploit:** Batalhas PvP entre jogadores comuns não emitem `TRAINER_BATTLE_WON`. Recompensas são aplicadas exclusivamente contra NPCs válidos, aplicando cooldown estrito por ID de treinador (ex: líderes de ginásio pagam apenas 1 vez a cada 24 horas).

### 3.3 Criador Pokémon (Breeding & Hatching)
* **Mod ID:** `cobblemon`, `cobbreeding`
* **API / Evento:** `com.cobblemon.mod.common.api.events.pokemon.EggHatchEvent` e eventos de criação de ovos.
* **Contexto Extraído:** `egg_uuid`, espécie do filhote, pais (quando disponível), jogador criador e jogador chocador.
* **Anti-Exploit:** Cada ovo possui UUID único (`egg_uuid`). O sistema de deduplicação impede que o mesmo ovo gere recompensa mais de uma vez. Ovos obtidos por trade ou comandos administrativos são ignorados.

### 3.4 Cuidador de Pasture (Manejo Consciente)
* **Mod ID:** `cobblemon`, `cobblemon_pasture`
* **API / Evento:** Eventos de interações manuais com Pasture e verificação de diversidade por demanda.
* **Anti-Exploit Crítico:** Em conformidade estrita com as diretrizes do projeto, **não existe farm passivo por tick**. O sistema não recompensa Pokémon parados no Pasture nem chunks carregados com o jogador AFK. A progressão ocorre via **coleta manual validada** (quando o jogador extrai o item pessoalmente) ou via **contratos de entrega** no menu.

### 3.5 Paleontólogo (Fósseis & Arqueologia)
* **Mod ID:** `cobblemon`, `cobblemon_fossils`
* **API / Evento:** Eventos de revivificação de fósseis em máquinas arqueológicas.
* **Contexto Extraído:** `fossil_process_id`, UUID do Pokémon revivido, item de origem e jogador responsável.
* **Anti-Exploit:** Apenas revivificações concluídas em estações válidas geram progresso. Ações de mover itens no inventário ou fechar a interface precocemente são ignoradas.

### 3.6 Especialista em Raids (Raid Dens / Bosses)
* **Mod ID:** `raiddens`, `cobblemon_raids`
* **API / Evento:** Eventos de finalização de raid em cooperação.
* **Contexto Extraído:** `raid_id`, tier da raid (1 a 7), espécie do boss, dano causado pelo jogador e turnos jogados.
* **Anti-Exploit:** Validação de participação mínima (ex: mínimo de 5% de dano do boss ou no mínimo 2 turnos jogados). Jogadores que entram no último segundo apenas para receber prêmio sem contribuir são rejeitados. Apenas uma recompensa por `player_uuid + raid_id`.

---

## 4. Matriz de Profissões Pokémon

| Profissão | Categoria | Marco de Desbloqueio | Slot Alocado | Ação Principal | Requisito da Licença |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Pesquisador Pokémon** | `POKEMON_SPECIALIZATION` | Rank 25 | Especialização Pokémon | `POKEMON_CAPTURED`, `DEX_ENTRY_ADDED` | Registrar 5 espécies diferentes na Pokédex |
| **Paleontólogo** | `POKEMON_SPECIALIZATION` | Rank 30 | Especialização Pokémon | `FOSSIL_REVIVED` | Reviver 1 fóssil válido em máquina |
| **Criador Pokémon** | `POKEMON_SPECIALIZATION` | Rank 35 | Especialização Pokémon | `EGG_CREATED`, `EGG_HATCHED` | Criar 1 ovo e chocar 1 ovo válido |
| **Cuidador de Pasture** | `POKEMON_SPECIALIZATION` | Rank 40 | Especialização Pokémon | `PASTURE_TASK_COMPLETED` | Concluir 1 coleta manual ou entregar 16 itens |
| **Treinador da Liga** | `POKEMON_SPECIALIZATION` | Rank 45 | Especialização Pokémon | `TRAINER_BATTLE_WON` | Derrotar 3 treinadores NPC diferentes |
| **Especialista em Raids** | `POKEMON_SPECIALIZATION` | Rank 50 | Especialização Pokémon | `RAID_CLEARED` | Concluir 1 Raid com participação mínima |

*Nota: O jogador pode manter no máximo 2 Jobs comuns ativos e **1 Especialização Pokémon** ativa simultaneamente. A troca de especialização possui cooldown padrão de 6 horas.*

---

## 5. Chave de Especialista e Caixa de Especialista

Para coroar a progressão de carreira no endgame:
* **Chave de Especialista (`SPECIALIST_KEY`):** Obtida exclusivamente através de Contratos Semanais de Especialização, Raids de alto tier e marcos de nível da especialização. Não pode ser comprada por VIP, moedas ou RankUp comum.
* **Caixa de Especialista (`specialist_crate`):** Integrada nativamente ao `CrateRewardGateway`. Entrega cosméticos exclusivos, títulos, molduras, boosters temporários e itens de exploração. **Nunca entrega** Pokémon lendários, shinies, Mega Stones em massa ou vantagens P2W de PvP.
