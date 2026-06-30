# Lacunas do Módulo de Crates

Este documento lista o que ainda não está exposto pelo módulo de Crates no estado atual do código.

Atualização: `/crate massopen`, `/crates claim` e a edição avançada de raridades, chaves, milestones, recompensas e localizações já foram registrados. As lacunas abaixo permanecem em aberto.

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

## 3. KeyDefinition: edição avançada já exposta

Hoje o módulo expõe:
- criação da chave
- nome
- tipo virtual/física
- ativação
- ícone físico
- vínculo de crate compatível
- lore
- permissão requerida
- sons de give/take
- comandos de entrega

Não há lacuna aberta neste subtópico no estado atual do módulo.

## 4. CrateRarity: edição avançada já exposta

Hoje o módulo permite adicionar, remover e editar raridades existentes por crate.

Não há lacuna aberta neste subtópico no estado atual do módulo.

## 5. CrateReward: edição avançada já exposta

Hoje o módulo permite criar, renomear, alterar peso, raridade, ativação, ícone, tipo, lore, permissões, limites, broadcast, visibilidade, milestone-only, ordem de exibição, remoção e duplicação por comando.

Não há lacuna aberta neste subtópico no estado atual do módulo.

## 6. CrateMilestone: edição avançada já exposta

Hoje o módulo permite adicionar e editar milestones existentes.

Não há lacuna aberta neste subtópico no estado atual do módulo.

## 7. CrateLocation: edição avançada já exposta

Hoje o módulo permite listar, remover, criar vínculo e editar localizações existentes.

Não há lacuna aberta neste subtópico no estado atual do módulo.

## 8. Configuração visual e de preview

Os domínios já existem:
- `CratePreviewConfig`
- `CrateAnimationConfig`
- `CrateVisualConfig`
- `CrateParticleConfig`

Mas ainda não há comandos ou menus dedicados para editar esses blocos de configuração de forma completa.

## 9. Ordem sugerida para implementação

Se a ideia for fechar as lacunas por prioridade prática, a sequência mais útil é:
1. configuração visual/preview/animação
2. edição avançada de requisitos da crate
