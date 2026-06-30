# Lacunas do Módulo de Crates

Este documento lista o que ainda não está exposto pelo módulo de Crates no estado atual do código.

Atualização: `/crate massopen` e `/crates claim` já foram registrados. As lacunas abaixo permanecem em aberto.

A análise foi feita comparando:
- a árvore de comandos em `CrateCommand`
- os menus do módulo (`CrateMainEditorMenu`, `CrateEditMenu`, `CrateKeyEditorMenu`, `CrateRewardListMenu`, `CratePreviewMenu`)
- os serviços e domínios do módulo

Quando eu digo "falta", é uma inferência baseada no código atual: há serviço, menu ou dado no domínio, mas não há comando ou fluxo de edição correspondente.

## 1. Comandos citados pelo código, mas já registrados

| Comando | Evidência no código | Situação atual |
|---|---|---|
| `/crate massopen <crate> [quantidade]` | O `CratePreviewMenu` exibe o botão "Abrir Multiplo" e o `CrateOpeningService` já tem `massOpen(...)` | Implementado em `CrateCommand` |
| `/crates claim` | O `CratePendingDeliveryService` já implementa `claimDeliveries(...)` e as mensagens de overflow instruem o jogador a usar esse comando | Implementado em `CrateCommand` |

## 2. CrateDefinition: campos ainda sem comando/menu de edição

O editor atual cobre nome, descrição, ícone, ativação, tipo de abertura, chave, custo, cooldown, permissão, raridades, milestones e local.

Ainda não há comando para expor:
- `previewConfig`
- `animationConfig`
- `visualConfig`
- `lore`
- `oneTimeUse`
- `requirePhysicalKey`
- `requireVirtualKey`
- `RequirementLogic`
- `alternativeCosts`

## 3. KeyDefinition: campos ainda sem comando/menu de edição

O módulo já expõe:
- criação da chave
- nome
- tipo virtual/física
- ativação
- ícone físico
- vínculo de crate compatível via `addcrate`

Ainda faltam comandos para:
- editar `lore`
- editar `requiredPermission`
- editar `giveSound`
- editar `takeSound`
- editar `giveCommands`
- remover crates compatíveis
- substituir a lista de crates compatíveis em lote

## 4. CrateRarity: edição parcial apenas

Hoje o módulo permite adicionar e remover raridades por crate.

Ainda faltam comandos para editar raridades existentes:
- `name`
- `color`
- `weight`
- `icon`
- `lore`
- `active`
- `priority`
- `displayOrder`

## 5. CrateReward: edição parcial apenas

Hoje o módulo permite criar, renomear, alterar peso, raridade, ativação e ícone.

Ainda faltam comandos para:
- alterar `type` entre `ITEM` e `COMMAND`
- editar `lore`
- editar `items`
- editar `commands`
- editar `requiredPermission`
- editar `blockingPermissions`
- editar `globalLimit`
- editar `playerLimit`
- editar `broadcast`
- editar `broadcastMessage`
- editar `playerMessage`
- editar `visibleInPreview`
- editar `milestoneOnly`
- editar `displayOrder`
- remover/duplicar recompensa por comando

## 6. CrateMilestone: edição parcial apenas

Hoje o módulo permite adicionar milestone, mas não editar os existentes.

Ainda faltam comandos para:
- alterar `name`
- alterar `description`
- alterar `rewardId`
- alterar `requiredOpenings`
- alterar `repeatable`
- alterar `active`
- alterar `displayOrder`
- remover milestone por comando

## 7. CrateLocation: configuração ainda incompleta

Hoje o módulo permite listar, remover e criar vínculo com `setlocation`.

Ainda faltam comandos para editar por local:
- `hologramTemplate`
- `hologramOffsetY`
- `hologramEnabled`
- `particleEnabled`
- `active`

## 8. Configuração visual e de preview

Os domínios já existem:
- `CratePreviewConfig`
- `CrateAnimationConfig`
- `CrateVisualConfig`
- `CrateParticleConfig`

Mas ainda não há comandos ou menus dedicados para editar esses blocos de configuração de forma completa.

## 9. Ordem sugerida para implementação

Se a ideia for fechar as lacunas por prioridade prática, a sequência mais útil é:
1. edição de recompensa avançada
2. edição de raridade avançada
3. edição de chave avançada
4. edição de milestone
5. configuração visual/preview/animação
6. edição avançada de requisitos da crate
