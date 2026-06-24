# Módulo de Trabalhos e Profissões — `/jobs`

O Módulo de Trabalhos e Profissões do **BigBangEssentials** permite que jogadores escolham profissões e ganhem dinheiro e experiência (XP) ao realizar ações específicas no servidor (como cortar árvores, minerar, construir, caçar, pescar, etc.).

---

## 1. Visão Geral e Arquitetura Modular

O módulo foi desenvolvido utilizando uma arquitetura orientada a serviços altamente desacoplada para garantir que novas mecânicas, habilidades e integrações com mods externos (como Create, Farmer's Delight, etc.) possam ser adicionadas futuramente sem afetar o núcleo do sistema.

Abaixo está a divisão de responsabilidades das classes principais do módulo:

| Classe / Serviço | Responsabilidade |
|---|---|
| `JobDefinition` | Representa os metadados e configurações estáticas de uma profissão (lidos a partir dos arquivos JSON). |
| `JobRegistry` | Armazena e expõe todas as profissões carregadas e registradas no sistema. |
| `JobManager` | Ponto central de orquestração do módulo. Gerencia o cache de dados dos jogadores ativos e do ciclo diário de reset. |
| `JobProgress` | Modelo que armazena os dados de progresso de um jogador em uma profissão específica (nível, XP, pontos, etc.). |
| `JobProgressService` | Controla operações de carreira de jogadores (entrada, saída e validação de limites de slots de trabalho). |
| `JobExperienceService` | Gerencia a atribuição de XP, calculando o ganho líquido e salvando os dados. |
| `JobLevelService` | Executa validações de subida de nível, recompensas de nível (comandos) e efeitos visuais/sonoros de Level Up. |
| `JobRewardService` | Calcula os ganhos de moedas baseando-se nos multiplicadores de nível e passivas de habilidades do jogador. |
| `JobDailyLimitService` | Controla o limite diário global e individual de cada profissão, aplicando bônus e permitindo pagamentos parciais. |
| `JobPermissionService` | Gerencia a checagem e resolução de permissões (VIPs de ganhos, limite de slots ativos, etc.). |
| `JobSkillService` | Valida pré-requisitos, custos e dependências circulares de habilidades, processando a compra de upgrades. |
| `JobActionRegistry` | Registra e valida os tipos de ações suportadas pelo sistema (ex: `BREAK_BLOCK`, `PLACE_BLOCK`, etc.). |
| `JobActionHandler` | Processa o contexto de uma ação (`JobActionContext`) executada por um jogador e ativa os cálculos de recompensas. |
| `JobRepository` | Camada de persistência integrada ao banco de dados principal do modpack (MySQL/SQLite). |
| `JobRankingService` | Gerencia e armazena em cache o Top 10 de jogadores de cada profissão, evitando consultas pesadas. |
| `JobMessageService` | Centraliza o envio de alertas visuais (ActionBar), mensagens de chat estruturadas e avisos de limite diário. |
| `JobCommandService` | Implementa a lógica por trás dos subcomandos normais de jogador. |
| `JobAdminCommandService` | Implementa a lógica por trás dos subcomandos administrativos (`/jobsadmin`). |
| `JobConfigurationLoader` | Lê os arquivos de configuração do disco e gerencia a exportação dos arquivos default da JAR. |
| `JobConfigurationValidator` | Valida a integridade lógica e sintática das configurações de carreiras. |

---

## 2. Profissões Iniciais

O sistema possui 10 profissões iniciais configuradas por padrão. Seus IDs internos e nomes em português são:

* **woodcutter** (Lenhador): Ganhe experiência e dinheiro ao cortar árvores, madeira e recursos vegetais.
* **miner** (Minerador): Ganhe experiência e dinheiro ao minerar minérios, pedras e recursos subterrâneos.
* **builder** (Construtor): Ganhe experiência e dinheiro ao construir estruturas e colocar blocos permitidos.
* **blacksmith** (Ferreiro): Ganhe experiência e dinheiro ao fundir, reparar, transformar e trabalhar materiais metálicos.
* **farmer** (Fazendeiro): Ganhe experiência e dinheiro ao plantar, colher e cuidar de cultivos, animais e recursos agrícolas.
* **ranger** (Caçador): Ganhe experiência e dinheiro ao derrotar criaturas e entidades configuradas.
* **explorer** (Explorador): Ganhe experiência e dinheiro ao descobrir regiões, estruturas, biomas e marcos do mundo.
* **crafter** (Artesão): Ganhe experiência e dinheiro ao fabricar itens, componentes e equipamentos.
* **culinarian** (Culinário): Ganhe experiência e dinheiro ao preparar alimentos, bebidas e receitas culinárias.
* **magician** (Mago): Ganhe experiência e dinheiro ao utilizar magia, rituais, encantamentos e recursos arcanos configurados.

---

## 3. Configurações e Compatibilidade

As configurações globais do módulo residem em `world/serverconfig/bigbangessentials/jobs.json`.

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

Cada profissão possui um arquivo de configuração JSON correspondente a seu ID dentro de `world/serverconfig/bigbangessentials/jobs/` (ex: `woodcutter.json`).
O sistema de carregamento aceita de forma compatível as seguintes variações de chaves:
1. **Limite Diário da Profissão**: Pode ser configurado como `"daily-limit": X` ou `"max-daily-earnings": X`.
2. **Settings de Pontos de Habilidade**: Pode ser configurado como `"skill-points-every": Y` ou no formato estruturado:
   ```json
   "skill-point-settings": {
     "every": Y
   }
   ```

---

## 4. Árvore de Habilidades (Skills)

Cada profissão pode declarar sua própria árvore de upgrades e passivas. O sistema suporta os seguintes efeitos padrão por rank da passiva:

* `money-multiplier`: Multiplicador percentual aditivo de moedas por rank (ex: `0.01` = +1% de ganhos).
* `xp-multiplier`: Multiplicador percentual aditivo de XP ganho por rank.
* `double-drop-chance`: Chance aditiva de obter drop duplo ao quebrar blocos.
* `extra-drop-chance`: Chance aditiva de drop adicional de itens da tabela de drops.
* `daily-limit-multiplier`: Multiplicador de aumento no limite diário pessoal de dinheiro do trabalho.
* `action-reward-chance`: Chance aditiva de ativar recompensas extras nas ações.

As habilidades também suportam validação automática de dependências (como nível mínimo, custo de pontos e pré-requisitos no formato `habilidade:rank`). Dependências circulares e de IDs inexistentes são rejeitadas e impedem o mod de iniciar com uma configuração corrompida.

---

## 5. VIP Multipliers (Bônus de Ganhos)

O módulo lê as permissões do jogador para aplicar bônus de economia e limites:
* **Ganhos VIP**: `jobs.ganhos.<percentual>` (ex: `jobs.ganhos.10` aumenta em 10% os ganhos de moedas: `1.10x`).
* **Limite de Profissões**: `jobs.limite.<quantidade>` (ex: `jobs.limite.3` permite ter até 3 profissões simultâneas).
* **XP VIP**: `jobs.xp.<percentual>`.
* **Limite Diário**: `jobs.limitediario.<percentual>`.

**Regras obrigatórias:**
* A maior permissão válida prevalece (ex: se o jogador possui `jobs.ganhos.10` e `jobs.ganhos.20`, o multiplicador será de `1.20x`).
* As permissões **não** são somadas.
* Permissões com valores negativos ou malformadas são ignoradas.

---

## 6. Sistema Anti-Exploit

Para assegurar uma economia equilibrada, o módulo possui a interface `JobAntiExploitService` e o enum `JobExploitReason` que contêm os seguintes motivos padrão de bloqueio:

* `JOB_NOT_ACTIVE`: Jogador não possui o trabalho ativo.
* `NO_PERMISSION`: Jogador não possui permissão para o trabalho.
* `PLAYER_AFK`: Jogador ausente (AFK) com base no sistema de AFK do mod.
* `DAILY_LIMIT_REACHED`: Limite diário de ganhos (global ou específico do trabalho) já foi atingido.
* `EVENT_CANCELLED`: Algum mod ou sub-evento cancelou a ação.
* `INVALID_SOURCE`: Ação originada de fonte inválida.
* `FAKE_PLAYER`: Ação executada por um Fake Player (automação ou máquinas).
* `PLAYER_PLACED_BLOCK`: Bloco a ser quebrado foi anteriormente colocado por um jogador (marcado pelo `BlockProtectionManager`).
* `SPAWNER_ENTITY`: Entidade derrotada foi gerada por um gerador de criaturas (spawner).
* `REPEATED_POSITION`: Evento de bloco executado repetidamente nas mesmas coordenadas.
* `ACTION_COOLDOWN`: Cooldown de ação por posição ou jogador ativo.
* `DUPLICATE_EVENT`: Ação duplicada recebida no mesmo tick.
* `NOT_CONFIGURED`: Ação ou recurso alvo não configurado para dar recompensas.

---

## 7. Comandos e Permissões

### Comandos de Jogador
* `/jobs`: Perfil pessoal do jogador, estatísticas, alertas e trabalhos ativos.
* `/jobs list`: Lista profissões e botões interativos para entrar ou sair.
* `/jobs entrar <profissao>`: Ingressa na carreira desejada.
* `/jobs sair <profissao>`: Abandona a carreira (sujeito a resetar o progresso se configurado).
* `/jobs info [profissao]`: Lista ações de recompensas detalhadas.
* `/jobs progresso [profissao]`: Mostra a barra de XP da profissão informada.
* `/jobs habilidades <profissao>`: Lista árvore de habilidades passivas e botões de upgrade.
* `/jobs habilidade <profissao> desbloquear <habilidade>`: Investe pontos de habilidades obtidos ao subir de nível.
* `/jobs ganhos`: Exibe total acumulado hoje e limite global diário.
* `/jobs top <profissao>`: Top 10 maiores níveis da profissão no servidor.
* `/jobs notificacoes <on|off>`: Liga/desliga avisos rápidos na actionbar.
* `/jobs ajuda`: Lista os comandos do mod.

### Comandos de Administrador
* `/jobsadmin reload`: Recarrega configurações de forma segura (preservando estado e caches).
* `/jobsadmin info <jogador> [profissao]`: Consulta detalhes de outro jogador (online/offline).
* `/jobsadmin entrar <jogador> <profissao>`: Adiciona jogador na profissão desejada.
* `/jobsadmin sair <jogador> <profissao>`: Remove jogador da profissão desejada.
* `/jobsadmin setlevel <jogador> <profissao> <nivel>`: Define nível da profissão de um jogador.
* `/jobsadmin addxp <jogador> <profissao> <quantidade>`: Concede XP de profissão ao jogador.
* `/jobsadmin removexp <jogador> <profissao> <quantidade>`: Remove XP.
* `/jobsadmin reset <jogador> [profissao]`: Reseta progresso de um ou todos os trabalhos.
* `/jobsadmin resetganhos <jogador>`: Reseta os ganhos acumulados hoje para o jogador.
* `/jobsadmin pontos <jogador> <profissao> adicionar/remover <quantidade>`: Altera pontos de habilidades disponíveis.
* `/jobsadmin debug <on|off>`: Liga modo debug global.

---

## 8. Como Estender o Sistema

### Adicionar uma Nova Profissão
1. Crie um arquivo JSON com o ID da nova profissão (ex: `merchant.json`) dentro de `world/serverconfig/bigbangessentials/jobs/`.
2. Siga a estrutura contendo `id`, `display-name`, `enabled`, `permission`, `actions`, `skills`, etc.
3. Adicione o ID no array `defaultJobs` em `JobsConfig.java` se desejar que ele seja criado como template padrão no JAR.

### Adicionar uma Ação Futura
1. Registre o identificador da ação no `JobActionRegistry` chamando `JobActionRegistry.registerActionType("MINHA_ACAO")`.
2. Escute os eventos do NeoForge apropriados no seu Listener (ex: `JobsEventListener`).
3. Quando a ação ocorrer, crie um `JobActionContext` contendo as informações coletadas do evento.
4. Chame `JobsManager.getInstance().processAction(player, "MINHA_ACAO", target, registryId)` passando o contexto e os alvos.

### Integrar com Novos Mods
O módulo de Jobs foi concebido para escutar eventos genéricos. Para integrar mods de terceiros:
1. Registre os blocos, itens ou entidades específicos do mod no arquivo JSON da profissão (ex: `"create:zinc_ore"` nas ações de `BREAK_BLOCK` no arquivo `miner.json`).
2. Caso o mod utilize eventos de quebra/construção/interação customizados que não disparam os eventos vanilla padrão do NeoForge, registre um novo `SubscribeEvent` no `JobsEventListener.java` direcionado para a API de eventos daquele mod e dispare a chamada para `JobsManager.getInstance().processAction(...)`.

---

## 9. Resolução de Problemas (Troubleshooting)

**1. O mod não carrega as novas configurações depois de alterar os arquivos JSON.**
* Certifique-se de executar `/jobsadmin reload`. Se houver algum erro de sintaxe nos JSONs (ex: vírgula sobrando, colchete não fechado) ou dependência de habilidades inválidas, o mod rejeitará as mudanças e manterá as configurações estáveis anteriores na memória para evitar a corrupção do progresso dos jogadores ativos. Verifique os logs de erro no arquivo de log do console do servidor.

**2. Jogadores VIPs não estão recebendo os bônus de ganhos.**
* Verifique se o jogador possui o nó de permissão exato, por exemplo, `jobs.ganhos.20` para 20% de bônus de moedas. Certifique-se também de que o gerenciador de permissões configurado no mod principal está ativo e sincronizado com os dados do jogador.
