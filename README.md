<p align="right">
  🌍 Leia em: <a href="README.en.md">English</a>
</p>

# BigBangEssentials

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-blue.svg)](https://minecraft.net)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.16.9+-blueviolet.svg)](https://fabricmc.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.179+-green.svg)](https://neoforged.net)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.2.6+build.366-blue)](https://github.com/pedro-dalben/BigBangEssentials)

Plataforma modular de gerenciamento de servidores Minecraft para Fabric e NeoForge. Oferece economia, jobs, administração, teleporte, utilidades para jogadores, chat, crates, hologramas, tablist, rankup, PokeMarket e dashboard web — tudo persistido em SQLite ou MySQL com suporte a módulos configuráveis.

---

## Sumário

- [Visão geral](#visão-geral)
- [Compatibilidade](#compatibilidade)
- [Funcionalidades](#funcionalidades)
- [Arquitetura](#arquitetura)
- [Requisitos](#requisitos)
- [Instalação](#instalação)
- [Primeira configuração](#primeira-configuração)
- [Arquivos de configuração](#arquivos-de-configuração)
- [Banco de dados](#banco-de-dados)
- [Permissões](#permissões)
- [Comandos](#comandos)
- [Integrações](#integrações)
- [API para desenvolvedores](#api-para-desenvolvedores)
- [Desenvolvimento](#desenvolvimento)
- [Testes](#testes)
- [Build e artefatos](#build-e-artefatos)
- [Releases](#releases)
- [Solução de problemas](#solução-de-problemas)
- [Segurança](#segurança)
- [Contribuição](#contribuição)
- [Créditos](#créditos)
- [Licença](#licença)

---

## Visão geral

BigBangEssentials é um mod server-side para servidores Minecraft rodando **Fabric** ou **NeoForge**. Ele substitui comandos vanilla (como `/msg`, `/tell`, `/tag`) e adiciona dezenas de sistemas prontos para servidores:

- Economia com pagamentos, saldos e proteção anti-exploit
- Sistema de jobs com profissões, XP, níveis, contratos e integração com Cobblemon
- Moderação completa (ban, kick, mute, jail, freeze, vanish)
- Teleporte (homes, warps, spawn, TPA, /back, random TP)
- Chat com mensagens privadas, canais, tags, AFK, socialspy
- Crates com keys, recompensas, animações e hologramas
- Kits, rankup, lojas administrativas
- PokeMarket — mercado de Pokémon entre jogadores (Cobblemon)
- Dashboard web com API REST e WebSocket
- PlaceholderAPI própria, LuckPerms e FTB Ranks

O mod funciona exclusivamente no servidor. Clientes vanilla conseguem conectar sem instalar nada.

---

## Compatibilidade

| Componente | Versão | Status |
|---|---|---|
| Minecraft | 1.21.1 (range: 1.21.1 – 1.21.10) | Estável |
| Java | 21 (Corretto recomendado) | Obrigatório |
| Fabric Loader | 0.16.9+ | Estável |
| Fabric API | 0.102.0+ para 1.21.1 | Obrigatório (Fabric) |
| NeoForge | 21.1.179+ | Estável |
| Cobblemon | 1.7.3+ para 1.21.1 | Opcional |
| Ambiente | Server-side (clientes vanilla) | Confirmado |
| Banco primário | SQLite (padrão) | Suportado |
| Banco alternativo | MySQL 8+ | Suportado |

> **Nota:** O suporte a NeoForge cobre até 1.21.10. A versão 1.21.11 introduz mudanças incompatíveis na API.

---

## Funcionalidades

### Economia e progressão

- Sistema de moeda (`/balance`, `/pay`, `/baltop`)
- Comandos administrativos (`/eco`, `/setworth`)
- Gems — moeda secundária com reservas, captura e expiração
- Vault API para integração com outros mods
- Limites diários de ganho em jobs
- Anti-exploit: cooldowns, rate limit, proveniência de blocos
- Rankup com ranks configuráveis por JSON
- Integração rankup + jobs (milestones compartilhados)

### Administração

- Ban, tempban, banip, unban, kick, kickall
- Mute, mutelist, jail (múltiplas celas configuráveis)
- Freeze, freezeall, vanish, sudo
- Inspeção de inventário (`/invsee`, `/enderchest`)
- Gerenciamento de jogadores (`/whois`, `/seen`, `/playtime`)
- Comandos de servidor (`/broadcast`, `/time`, `/weather`, `/kill`)
- Comando administrativo raiz: `/bigbangessentials` (alias: `/bbe`)

### Teleporte e utilidades

- Homes (múltiplas, com limites por permissão)
- Warps (globais e pessoais)
- Spawn configurável
- TPA, TPA here, TP accept/deny/cancel
- `/back` — retornar à última localização
- TPR (aleatório), TPPOS, TP all, TP override
- `/top`, `/jump`, `/jumpto`, `/bottom`
- Kits (com cooldowns e preview em menu)
- Baús portáteis: `/anvil`, `/craft`, `/grindstone`, `/smithing`, `/stonecutter`, `/loom`, `/cartography`, `/book`, `/enchantingtable`
- Menus de teleporte configuráveis por YAML

### Chat e comunicação

- Mensagens privadas (`/msg`, `/reply`, `/tell`, `/whisper`, `/w`)
- Canais de chat dinâmicos (configuráveis por JSON)
- Tags de chat customizáveis
- Sistema AFK com detecção automática
- SocialSpy, mail, ignore
- Integração com DiscordSRV, DCIntegration, SDLink (via reflection)
- Placeholders customizados no chat e tablist

### Cobblemon

- **PokeMarket** — mercado de Pokémon entre jogadores
  - Anúncios por tempo determinado ou até vender
  - Trocas diretas entre jogadores
  - Sistema de claims e notificações
  - Expiração automática de anúncios
  - Tabela de preços sugeridas por rarity/IVs
- Jobs com integração Cobblemon:
  - Treinador, Criador, Colecionador, Paleontólogo, Pastoreio, Reides
  - Breeder jobs para incubação
  - Boss/raid den jobs
  - Fossil processing jobs
  - Pasture collection e diversity scoring
  - Pesquisador (Pokédex completion)
- Rankup com requisitos Cobblemon

### Infraestrutura

- Banco SQLite (padrão) ou MySQL/HikariCP
- 22 migrations versionadas com soma de verificação
- Pool de conexões com execução assíncrona
- Geração automática de configuração na primeira inicialização
- Dashboard web com REST API e WebSocket
- Autenticação com Discord OAuth e registro local
- Mapa de jogadores ao vivo no dashboard
- Analytics de sessão dos jogadores
- Sistema de módulos configurável — 17 módulos registrados
- PlaceholderAPI própria (30+ placeholders nativos)

---

## Arquitetura

```
BigBangEssentials/
├── common/          # Código compartilhado (lógica principal, comandos, DB, UI)
├── fabric/          # Entrypoint Fabric + mixins + shim de event bus
├── neoforge/        # Entrypoint NeoForge + integração nativa com event bus
├── docs/            # Documentação técnica
└── gradle/          # Wrapper e convenções
```

A separação funciona em três camadas:

1. **common** — Contém toda a lógica do mod: comandos, economia, jobs, chat, database, dashboard, placeholders, permissões, integrações. Não depende de loader específico.
2. **fabric** — Entrypoint `FabricModEntrypoint` + mixins + um shim do event bus do NeoForge para reusar listeners. Usa Fabric Loom para build.
3. **neoforge** — Entrypoint `NeoForgeModEntrypoint` com `@Mod` + listeners nativos do NeoForge. Usa NeoForge ModDev para build.

A comunicação entre common e os loaders é feita pela interface `PlatformProvider`, que abstrai MinecraftServer, diretórios, event bus e verificação de mods carregados.

### Módulos

| Módulo | Depende de | Ativado por padrão |
|---|---|---|
| database | — | Sim |
| economy | database | Sim |
| chat | — | Sim |
| moderation | — | Sim |
| teleportation | — | Sim |
| kits | — | Sim |
| customcommands | — | Sim |
| webdashboard | — | Sim |
| jobs | economy, database | Sim |
| rankup | economy, database | Sim |
| crates | database | Sim |
| holograms | — | Sim |
| shop | economy, database | Sim |
| adminshop | economy, database | Sim |
| cobblemon | — | Auto (se Cobblemon presente) |
| pokemarket | database, economy, cobblemon | Sim |
| tablist | — | Sim |

Cada módulo pode ser desabilitado via configuração. Dependências não satisfeitas bloqueiam a ativação.

---

## Requisitos

- **Java 21** (Corretto 21 recomendado)
- **Minecraft 1.21.1**
- **Fabric:** Fabric Loader 0.16.9+ e Fabric API
- **NeoForge:** NeoForge 21.1.179+
- **Cobblemon (opcional):** 1.7.3+ para 1.21.1
- **LuckPerms (opcional):** Para gerenciamento de permissões
- **FTB Ranks (opcional):** Para ranks avançados (NeoForge)
- **MySQL (opcional):** Para banco externo em produção

---

## Instalação

### Fabric

1. Instale o **Fabric Loader** para Minecraft 1.21.1.
2. Coloque a **Fabric API** (0.102.0+ para 1.21.1) em `mods/`.
3. Coloque o JAR `bigbangessentials-fabric-*.jar` em `mods/`.
4. (Opcional) Coloque o **Cobblemon** (Fabric) em `mods/` se for usar PokeMarket ou jobs Cobblemon.
5. Inicie o servidor. Os arquivos de configuração serão gerados automaticamente.
6. Configure permissões e ajuste a `database.json` se necessário.
7. Reinicie o servidor para aplicar as configurações.

### NeoForge

1. Instale o **NeoForge** 21.1.179+ para Minecraft 1.21.1.
2. Coloque o JAR `bigbangessentials-*.jar` em `mods/`.
3. (Opcional) Coloque o **Cobblemon** (NeoForge) em `mods/` se for usar PokeMarket ou jobs Cobblemon.
4. Inicie o servidor. Os arquivos de configuração serão gerados automaticamente.
5. Configure permissões e ajuste a `database.json` se necessário.
6. Reinicie o servidor para aplicar as configurações.

> ⚠️ Os JARs Fabric e NeoForge **não são intercambiáveis**. Use o artefato correto para seu loader.

---

## Primeira configuração

1. Após iniciar o servidor, os configs estarão em:
   ```
   world/serverconfig/bigbangessentials/
   ```
   (a migração legado de `config/bigbangessentials/` é feita automaticamente)

2. Configure o banco em `database.json`:
   ```json
   {
     "enabled": true,
     "required": true,
     "type": "SQLITE",
     "sqlite": {
       "file": "bigbangessentials/database/bigbangessentials.db"
     }
   }
   ```

3. Configure permissões (exemplo com LuckPerms):
   ```
   /lp group default permission set bigbangessentials.player true
   /lp group moderator permission set bigbangessentials.moderation.* true
   /lp group admin permission set bigbangessentials.admin true
   ```

4. Verifique se o mod carregou:
   ```
   /bigbangessentials version
   ```

5. (Opcional) Divida o `config.json` em arquivos menores:
   ```
   /bigbangessentials config split
   ```

---

## Arquivos de configuração

```
world/serverconfig/bigbangessentials/
├── config.json              # Configuração principal (monolítica)
├── database.json            # Conexão com banco de dados
├── economy.json             # Configuração da economia
├── permissions.json         # Permissões internas
├── kits.json                # Definição de kits
├── modules.json             # Ativação/desativação de módulos
├── tablist.json             # Configuração da tablist
├── discord_auth.json        # Autenticação Discord para dashboard
├── custom_commands.json     # Comandos personalizados
├── rankup.json              # Definição de ranks
├── adminshop.json           # Catálogo da loja administrativa
├── tags.json                # Tags de chat
├── jobs/                    # Configurações de jobs
│   ├── global.json
│   ├── slots.json
│   ├── milestones.json
│   └── professions/*.json
├── menus/*.yml              # Menus customizáveis (YAML)
├── holograms/*.json         # Hologramas
├── badges/                  # Imagens de badges para chat
└── text/*.txt               # Páginas de texto customizadas
```

Quando o `config.json` monolítico é muito grande, o comando `/bigbangessentials config split` o divide em arquivos menores por sistema (`main.json`, `commands.json`, `chat.json`, `teleportation.json`, `moderation.json`, `webdashboard.json`, `items.json`, `afk.json`, `security.json`).

> Todos os arquivos ausentes são gerados automaticamente com valores padrão na primeira inicialização.

---

## Banco de dados

### SQLite (padrão)

- Arquivo local: `bigbangessentials/database/bigbangessentials.db`
- Pool: `maximumPoolSize=1` (WAL contention)
- Ideal para servidores pequenos/médios
- Nenhuma configuração externa necessária

### MySQL (produção)

Configure em `database.json`:

```json
{
  "type": "MYSQL",
  "mysql": {
    "host": "localhost",
    "port": 3306,
    "database": "bigbangessentials",
    "user": "bbe_user",
    "password": "senha_segura"
  },
  "pool": {
    "maximumPoolSize": 10,
    "minimumIdle": 2,
    "connectionTimeoutMs": 5000
  }
}
```

Recomendado para servidores com muitos jogadores concorrentes.

### Migrations

22 migrations versionadas executadas automaticamente na inicialização. Novo servidor → cria tudo. Servidor existente → executa apenas pendentes. Cada migration possui soma de verificação contra adulteração.

### Recomendações

- Faça backup regular do banco (especialmente antes de atualizar o mod)
- Em produção, MySQL é recomendado para desempenho e confiabilidade
- Não compartilhe credenciais de banco em lugares públicos

---

## Permissões

O sistema de permissões suporta LuckPerms, FTB Ranks, PEX ou o sistema interno (que assume OP para comandos administrativos).

Todas as permissões seguem o padrão `bigbangessentials.<módulo>.<ação>`.

### Permissões principais

| Permissão | Descrição | Padrão |
|---|---|---|
| `bigbangessentials.player.*` | Acesso a comandos básicos de jogador | true |
| `bigbangessentials.chat.*` | Comandos de chat (msg, reply, mail) | true |
| `bigbangessentials.economy.*` | Comandos de economia (balance, pay) | true |
| `bigbangessentials.teleport.*` | Comandos de teleporte (home, warp, tpa) | true |
| `bigbangessentials.kit.*` | Acesso a kits | true |
| `bigbangessentials.item.*` | Comandos de item (hat, repair) | true |
| `bigbangessentials.moderation.*` | Comandos de moderação | false |
| `bigbangessentials.admin.*` | Comandos administrativos | false |
| `bigbangessentials.jobs.*` | Sistema de jobs | true |
| `bigbangessentials.crates.*` | Gerenciamento de crates | true |
| `bigbangessentials.holograms.*` | Gerenciamento de hologramas | false |
| `bigbangessentials.rankup.*` | Sistema de rankup | true |
| `bigbangessentials.pokemarket.*` | PokeMarket | true |
| `bigbangessentials.tablist.*` | Configuração de tablist | false |
| `bigbangessentials.webdashboard.*` | Acesso ao dashboard | false |

Referência completa de permissões: [`docs/Wiki/PermissionSystem.md`](docs/Wiki/PermissionSystem.md) e [`permissions.md`](permissions.md)

---

## Comandos

O mod registra aproximadamente **110 comandos únicos** (sem contar aliases e subcomandos).

### Administrativos

| Comando | Descrição | Permissão |
|---|---|---|
| `/bigbangessentials` `/bbe` | Comando raiz administrativo | admin |
| `/eco` | Gerenciar economia | admin |
| `/ban` `/unban` `/banip` `/tempban` | Banimento | moderation |
| `/kick` `/kickall` | Expulsar jogadores | moderation |
| `/mute` `/unmute` `/mutelist` | Silenciar | moderation |
| `/jail` `/unjail` `/setjail` `/deljail` | Sistema de jail | moderation |
| `/freeze` `/unfreeze` `/freezeall` | Congelar jogadores | moderation |
| `/vanish` `/v` | Modo invisível | moderation |
| `/sudo` | Executar comando como outro | moderation |
| `/broadcast` `/bc` | Anunciar para todos | admin |
| `/gamemode` `/gms` `/gmc` `/gmsp` `/gma` | Alterar gamemode | admin |
| `/kill` | Matar jogador | admin |
| `/invsee` `/enderchest` | Inspecionar inventário | moderation |
| `/dashboard` | Gerenciar dashboard web | admin |

### Teleporte

| Comando | Descrição | Permissão |
|---|---|---|
| `/home` `/sethome` `/delhome` `/homes` | Homes | teleport |
| `/warp` `/setwarp` `/delwarp` `/warps` | Warps | teleport |
| `/spawn` `/setspawn` | Spawn | teleport |
| `/tpa` `/tpahere` `/tpaccept` `/tpdeny` | Pedidos de teleporte | teleport |
| `/tp` `/tphere` `/tpall` `/tppos` | Teleporte administrativo | admin |
| `/tpr` | Teleporte aleatório | teleport |
| `/back` | Voltar à última posição | teleport |
| `/top` `/jump` `/bottom` | Teleporte rápido | teleport |

### Economia

| Comando | Descrição | Permissão |
|---|---|---|
| `/balance` `/bal` | Ver saldo | economy |
| `/pay` | Pagar jogador | economy |
| `/baltop` | Ranking de saldos | economy |
| `/worth` `/sell` | Valor e venda de itens | economy |
| `/gems` | Carteira de gems | economy |

### Chat

| Comando | Descrição | Permissão |
|---|---|---|
| `/msg` `/tell` `/w` `/whisper` | Mensagem privada | chat |
| `/reply` `/r` | Responder mensagem | chat |
| `/mail` | Sistema de correio | chat |
| `/ignore` `/unignore` | Ignorar jogador | chat |
| `/socialspy` | Espionar mensagens | moderation |
| `/afk` `/away` | Ausente | chat |
| `/tags` | Gerenciar tags de chat | chat |

### Jobs

| Comando | Descrição | Permissão |
|---|---|---|
| `/jobs` | Visualizar e gerenciar jobs | jobs |
| `/jobsadmin` | Administrar jobs | admin |

### Crates

| Comando | Descrição | Permissão |
|---|---|---|
| `/crates` `/crate` | Gerenciar crates | crates |
| `/givekey` `/keygive` | Dar key a jogador | admin |

### Cobblemon

| Comando | Descrição | Permissão |
|---|---|---|
| `/pokemarket` `/gts` `/pm` | Mercado de Pokémon | pokemarket |

Referência completa de comandos: [`docs/Wiki/CommandsReference.md`](docs/Wiki/CommandsReference.md)

---

## Integrações

| Integração | Obrigatória | Loader | Versão | Comportamento sem a integração |
|---|---|---|---|---|
| **Cobblemon** | Não | Ambos | 1.7.3+ | Jobs Cobblemon e PokeMarket desativados |
| **LuckPerms** | Não | Ambos | API 5.4 | Usa sistema interno de permissões (OP) |
| **FTB Ranks** | Não | NeoForge | — | Apenas LuckPerms ou sistema interno |
| **Fabric API** | Sim (Fabric) | Fabric | 0.102.0+ | Mod não carrega sem |
| **DiscordSRV** | Não | Ambos | — | Chat roda sem integração Discord |
| **PlaceholderAPI** | Não | Ambos | — | PlaceholderManager próprio sempre ativo |

---

## API para desenvolvedores

### API pública

Pacote: `com.pedrodalben.bigbangessentials.api`

| Interface | Função |
|---|---|
| `EconomyAPI` | Operações financeiras (depósito, saque, saldo, transferência) |
| `BigBangEssentialsAPI` | Acesso central ao mod |
| `PlaceholderAPI` | Registro e resolução de placeholders |
| `ChatAPI` | Envio de mensagens |
| `PermissionAPI` | Verificação de permissões |
| `RankupAPI` | Consulta e promoção de ranks |

### Eventos

| Evento | Descrição |
|---|---|
| `EconomyDepositEvent` | Disparado ao depositar dinheiro |
| `EconomyWithdrawEvent` | Disparado ao sacar dinheiro |
| `GemBalanceChangedEvent` | Disparado ao alterar saldo de gems |
| `RankTransitionCompletedEvent` | Disparado ao completar transição de rank |

### Placeholders

30+ placeholders nativos: `{player}`, `{online}`, `{max}`, `{balance}`, `{job}`, `{job_level}`, `{rank}`, `{gems}`, `{ping}`, `{world}`, `{prefix}`, `{suffix}`, etc.

Desenvolvedores podem registrar placeholders customizados via `PlaceholderAPI.registerPlaceholder()`.

### Dashboard REST API

O dashboard web expõe uma REST API com endpoints para status do servidor, jogadores, logs, configuração e estatísticas. Autenticação via Discord OAuth ou registro local.

---

## Desenvolvimento

### Pré-requisitos

- JDK 21 (Corretto 21 recomendado)
- IntelliJ IDEA (Community Edition recomendada)
- Git

### Setup

```bash
git clone https://github.com/pedro-dalben/BigBangEssentials.git
cd BigBangEssentials
./gradlew idea
```

Abra o diretório no IntelliJ IDEA como projeto Gradle. A sincronização baixará automaticamente as dependências.

### Build

```bash
./gradlew build
```

### Tarefas especiais

```bash
./gradlew verifyCobblemonDependencies    # Verifica dependências Cobblemon
./gradlew verifyNoBundledCobblemon       # Garante que Cobblemon não está embutido no JAR
./gradlew test                           # Todos os testes (exige Docker para MySQL)
./gradlew mysqlIntegrationTest           # Testes de integração MySQL
./gradlew pokeMarketConcurrencyTest      # Testes de concorrência do PokeMarket
./gradlew pokeMarketFaultInjectionTest   # Testes de injeção de falhas
./gradlew runWithoutCobblemonTest        # Testa inicialização sem Cobblemon
```

### Artefatos gerados

| Loader | Caminho | Nome do arquivo |
|---|---|---|
| Fabric | `fabric/build/libs/` | `bigbangessentials-fabric-<version>+build.<N>.jar` |
| NeoForge | `neoforge/build/libs/` | `bigbangessentials-<version>+build.<N>.jar` |

---

## Testes

O projeto possui **90+ testes** organizados por módulo:

- **Unidade:** Domínios, validação, parsing, formatação
- **Integração:** Banco (SQLite + MySQL com Testcontainers), jobs, economy, crates
- **Concorrência:** PokeMarket, reservas de gems
- **Injeção de falhas:** PokeMarket, economia, banco
- **Mocking:** Permissões, placeholders, comandos

Testes MySQL exigem Docker ou variáveis de ambiente `BBE_TEST_MYSQL_*`.

---

## Releases

Atualmente não há releases publicados oficialmente no GitHub. Para usar o mod:

1. Compile a partir do código: `./gradlew build`
2. Os JARs estarão em `fabric/build/libs/` e `neoforge/build/libs/`
3. Consulte o `CHANGELOG.md` para histórico de versões

---

## Solução de problemas

| Problema | Causa possível | Solução |
|---|---|---|
| Mod não carrega | Java < 21 | Verifique `java -version`, instale JDK 21 |
| `fabric.mod.json` não encontrado | Loader incorreto | Use o JAR Fabric no Fabric Loader |
| Erro "Cobblemon class not found" | Cobblemon ausente | Instale Cobblemon ou desabilite módulos Cobblemon |
| Falha ao conectar MySQL | Credenciais inválidas | Verifique `database.json`, acesso e firewall |
| Comandos não aparecem | Módulo desabilitado | Verifique `modules.json` ou `config.json > modules` |
| Config ignorada | Cache do jogo | Pare o servidor, edite, reinicie |
| Permissão negada em comando de jogador | Sistema de permissões | Configure LuckPerms ou dê OP |
| Erro `configuration-cache` | Cache corrompido | `rm -rf .gradle/configuration-cache` |

---

## Segurança

- **Nunca publique** credenciais de banco de dados
- O dashboard web requer autenticação (Discord OAuth ou registro local)
- Endpoints administrativos são protegidos por permissão
- Configure permissões restritivas para comandos administrativos
- Faça backup regular do banco e configurações
- Reporte vulnerabilidades via GitHub Issues

---

## Contribuição

1. Abra uma issue descrevendo o bug ou melhoria
2. Faça fork do repositório
3. Crie uma branch descritiva
4. Mantenha commits organizados
5. Execute `./gradlew test` antes de abrir o PR
6. Documente alterações relevantes
7. Abra um pull request

---

## Créditos

- **Autor:** [pedrodalben](https://github.com/pedrodalben)
- **Repositório:** [github.com/pedro-dalben/BigBangEssentials](https://github.com/pedro-dalben/BigBangEssentials)
- **Inspiração:** O projeto foi inspirado pelo mod NeoEssentials (originalmente por MrWhiteFlamesYT), mas constitui uma reescrita completa e independente.

---

## Licença

O arquivo `LICENSE` neste repositório contém a licença **MIT**, com Copyright (c) 2025 ZeroG Network.

> **Nota:** Os metadados do mod (`gradle.properties`, `fabric.mod.json`) declararam historicamente "All Rights Reserved". Verifique o arquivo `LICENSE` para a licença vigente. Em caso de dúvida, consulte o mantenedor.

---

**BigBangEssentials** — Plataforma modular para servidores Minecraft.
