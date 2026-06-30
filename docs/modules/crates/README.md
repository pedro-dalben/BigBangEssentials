# Módulo de Crates — BigBangEssentials

## Visão Geral

O módulo de Crates do BigBangEssentials é um sistema completo de loot boxes (caixas de recompensas) com suporte a abrir cas, chaves físicas e virtuais, animações, hologramas, partículas, milestones, raridades, limites globais/por jogador, logs de auditoria e integração com economia.

## Arquitetura

O sistema segue uma arquitetura em camadas:

```
┌─────────────────────────────────────────────────────────┐
│                   Commands (/crates...)                  │
├─────────────────────────────────────────────────────────┤
│                     Menus (Editor GUI)                    │
├─────────────────────────────────────────────────────────┤
│        Services (CrateService, CrateKeyService,          │
│           RewardService, CrateOpeningService,            │
│                  CrateAuditService)                       │
├─────────────────────────────────────────────────────────┤
│       Repositories (JSON + JDBC, interfaces + impls)      │
├─────────────────────────────────────────────────────────┤
│   Persistence: JSON files (definições) + JDBC (estado)    │
└─────────────────────────────────────────────────────────┘
```

### Domínios Principais

- **CrateDefinition** — Definição de uma crate: nome, descrição, item de exibição, raridades, recompensas, milestones, configurações de preview/visual/animação, requisitos, cooldown, custo.
- **KeyDefinition** — Definição de chave: ID, nome, item físico, tipo (virtual/física), crates compatíveis, permissão requerida, sons.
- **CrateLocation** — Localização de uma crate no mundo: dimensão, coordenadas, configurações de holograma e partícula.
- **PlayerVirtualKeyBalance** — Saldo de chaves virtuais de um jogador.
- **PlayerCrateState** — Estado de um jogador por crate: cooldown, total de aberturas, progresso de milestones.
- **RewardRollState** — Estado global e por jogador de rolagem de recompensa (usado para limites).
- **CrateOpenAudit** — Log de auditoria de cada abertura.

### Serviços

| Serviço | Responsabilidade |
|---------|-----------------|
| `CrateService` | CRUD de crates, chaves, localizações, raridades, recompensas, milestones |
| `CrateKeyService` | Gerenciamento de chaves virtuais e físicas: dar, retirar, definir, inspecionar, consumir |
| `RewardService` | Seleção ponderada de raridade/recompensa, entrega de recompensas, limites |
| `CrateOpeningService` | Fluxo completo de abertura: idempotência, validação, consumo, cooldown, entrega, milestones |
| `CrateAuditService` | Criação e consulta de logs de auditoria |

### Repositórios

| Repositório | Persistência | Dados |
|-------------|-------------|-------|
| `JsonCrateRepository` | JSON | Definições de crates |
| `JsonKeyRepository` | JSON | Definições de chaves |
| `JsonCrateLocationRepository` | JSON | Localizações de crates |
| `JdbcPlayerVirtualKeyRepository` | JDBC | Saldos de chaves virtuais |
| `JdbcPlayerCrateStateRepository` | JDBC | Estado do jogador por crate |
| `JdbcRewardRollStateRepository` | JDBC | Estado de rolagem de recompensas |
| `JdbcCrateAuditRepository` | JDBC | Logs de auditoria |

## Lista de Funcionalidades

- [x] Definição completa de crates (JSON)
- [x] Sistema de chaves (físicas e virtuais)
- [x] Raridades com seleção por peso
- [x] Recompensas do tipo ITEM e COMMAND
- [x] Limites globais e por jogador por recompensa
- [x] Milestones (recompensas por número de aberturas)
- [x] Cooldown e custo econômico por abertura
- [x] Hologramas com ArmorStands nas localizações
- [x] Partículas (CIRCLE, SPIRAL, COLUMN, AURA)
- [x] Animações virtuais (GUI com rolagem de itens)
- [x] Animações físicas (partículas e sons no bloco)
- [x] Preview de recompensas
- [x] Editor gráfico completo
- [x] Confirmação para ações destrutivas
- [x] Logs de auditoria completos
- [x] Idempotência em aberturas
- [x] Integração com economia
- [x] Placeholders para menus
- [x] Permissões granulares
- [x] Suporte a GrantSources (ADMIN_COMMAND, STORE, VIP, EVENT, QUEST, TOURNAMENT, SYSTEM, OPENING, MILESTONE, MASS_OPEN)
- [x] Bloqueio de blocos contra explosões e quebra não autorizada
- [x] Abertura em massa (mass open)
- [x] Coleta de entregas pendentes (/crates claim)
- [x] Reset de cooldown
- [x] Drop de chaves físicas no mundo

> Lacunas detalhadas: [LACUNAS.md](LACUNAS.md)

## Guia Rápido

### 1. Criar uma Crate

```
/crate create minha_crate "Minha Crate"
```

### 2. Adicionar Raridades

```
/crate addrarity minha_crate comum "Comum" "#AAAAAA" 50
/crate addrarity minha_crate raro "Raro" "#FFD700" 30
/crate addrarity minha_crate lendario "Lendario" "#FF0000" 20
```

### 3. Adicionar Recompensas

```
/crate reward create minha_crate recompensa1 "Recompensa 1" comum
/crate reward create minha_crate recompensa2 "Recompensa 2" raro
```

### 4. Criar uma Chave

```
/crate key create chave_minha_crate "Chave da Minha Crate"
/crate key addcrate chave_minha_crate minha_crate
```

### 5. Vincular a um Bloco no Mundo

```
/crate setlocation minha_crate
```
(Clique no bloco desejado segurando o item de configuração)

### 6. Dar Chaves a Jogadores

```
/givekey chave_minha_crate jogador 1
```

### 7. Abrir a Crate

Clique com botão direito no bloco vinculado, ou use:
```
/crate open minha_crate
```

### 8. Visualizar/Editar pelo Editor

```
/crate editor
```

## Estrutura de Arquivos

```
common/src/main/java/com/pedrodalben/bigbangessentials/crates/
├── CrateManager.java
├── animation/
│   ├── CrateAnimationHandler.java
│   └── VirtualOpeningMenu.java
├── command/
│   ├── CrateCommand.java
│   ├── GiveKeyCommand.java
│   ├── KeyGiveCommand.java
│   └── config/
│       ├── CrateMessages.java
│       └── CratePermissions.java
├── domain/
│   ├── CrateAnimationConfig.java
│   ├── CrateDefinition.java
│   ├── CrateLocation.java
│   ├── CrateMilestone.java
│   ├── CrateOpenAudit.java
│   ├── CrateOpeningType.java
│   ├── CrateParticleConfig.java
│   ├── CratePreviewConfig.java
│   ├── CrateRarity.java
│   ├── CrateRequirements.java
│   ├── CrateReward.java
│   ├── CrateVisualConfig.java
│   ├── GrantSource.java
│   ├── ItemSerializer.java
│   ├── KeyDefinition.java
│   ├── ParticleShape.java
│   ├── PlayerCrateState.java
│   ├── PlayerVirtualKeyBalance.java
│   ├── RewardRollState.java
│   └── RewardType.java
├── hologram/
│   └── CrateHologramManager.java
├── integration/
│   └── CrateEconomyIntegration.java
├── listener/
│   ├── CrateBlockListener.java
│   └── CratePlayerListener.java
├── menu/
│   ├── AbstractCrateMenu.java
│   ├── CrateConfirmationMenu.java
│   ├── CrateEditMenu.java
│   ├── CrateKeyEditorMenu.java
│   ├── CrateMainEditorMenu.java
│   ├── CratePreviewMenu.java
│   └── CrateRewardListMenu.java
├── particle/
│   └── CrateParticleManager.java
├── persistence/
│   ├── JdbcCrateAuditRepository.java
│   ├── JdbcPlayerCrateStateRepository.java
│   ├── JdbcPlayerVirtualKeyRepository.java
│   ├── JdbcRewardRollStateRepository.java
│   ├── JsonCrateLocationRepository.java
│   ├── JsonCrateRepository.java
│   └── JsonKeyRepository.java
├── placeholder/
│   └── CratePlaceholderResolver.java
├── repository/
│   ├── CrateAuditRepository.java
│   ├── CrateLocationRepository.java
│   ├── CrateRepository.java
│   ├── KeyRepository.java
│   ├── PlayerCrateStateRepository.java
│   ├── PlayerVirtualKeyRepository.java
│   └── RewardRollStateRepository.java
└── service/
    ├── CrateAuditService.java
    ├── CrateKeyService.java
    ├── CrateOpeningService.java
    ├── CrateService.java
    └── RewardService.java
```

## Dependências

- **BigBangEssentials API** — EconomyService, PermissionAPI, Platform
- **Gson** — Serialização JSON
- **JDBC** — Persistência de dados de jogador
- **NeoForge** — Eventos de bloco, jogador, tick do servidor
