# Jobs Editor & Crate Tiers — Guia Operacional

## Visão Geral

O Jobs Editor é o sistema administrativo central para configurar todos os Jobs (profissões) do servidor BigBangCraft sem editar arquivos YAML manualmente. Ele utiliza menus visuais (estilo baú) com suporte complementar por comandos Brigadier.

### Acesso

Comando principal: `/jobsadmin editor`

Permissão mínima: `jobs.admin.editor.open`

## Catálogo de Jobs

O catálogo contém 17 profissões organizadas em duas categorias:

### Jobs Comuns (COMMON)

| Job ID | Display Name | Ações | Slot | Integração |
|--------|-------------|-------|------|------------|
| `miner` | Minerador | BREAK_BLOCK | COMMON_PRIMARY | Nenhuma |
| `lumberjack` | Lenhador | BREAK_BLOCK | COMMON_PRIMARY | Nenhuma |
| `farmer` | Agricultor | HARVEST_CROP | COMMON_PRIMARY | Nenhuma |
| `explorer` | Explorador | EXPLORE | COMMON_PRIMARY | Nenhuma |
| `fisher` | Pescador | FISH | COMMON_SECONDARY | Nenhuma |
| `artisan` | Artesão | CRAFT_ITEM | COMMON_SECONDARY | Nenhuma |
| `blacksmith` | Ferreiro | SMELT_ITEM | COMMON_SECONDARY | Nenhuma |
| `poke_chef` | PokéChef | CRAFT_ITEM, SMELT_ITEM | COMMON_SECONDARY | Nenhuma |
| `builder` | Construtor | PLACE_BLOCK | COMMON_SECONDARY | bigbangregions* |
| `merchant` | Comerciante | CONTRACT_DELIVERED | COMMON_SECONDARY | Nenhuma |

*Construtor fica indisponível (INTEGRATION_MISSING) até que o sistema de projetos/regiões esteja ativo.

### Especializações Pokémon (POKEMON_SPECIALIZATION)

| Job ID | Display Name | Ações | Integração |
|--------|-------------|-------|------------|
| `pokemon_researcher` | Pesquisador Pokémon | POKEMON_CAPTURED, DEX_ENTRY_ADDED | cobblemon |
| `paleontologist` | Paleontólogo | FOSSIL_REVIVED | fossils |
| `pokemon_breeder` | Criador Pokémon | EGG_CREATED, EGG_HATCHED | breeding |
| `pasture_keeper` | Cuidador de Pasture | PASTURE_TASK_COMPLETED | pasture |
| `league_trainer` | Treinador da Liga | TRAINER_BATTLE_WON | trainers |
| `raid_specialist` | Especialista em Raids | RAID_CLEARED | raid_dens |
| `pokemon_architect` | Arquiteto Pokémon | PLACE_BLOCK | bigbangregions* |

Especializações exigem: Rank mínimo, licença, slot POKEMON_SPECIALIZATION e integração ativa.

## Sistema de Três Níveis de Crate

Os tiers são lógicos e configuráveis:

| Tier ID | Display Name (padrão) | Propósito |
|---------|----------------------|-----------|
| `beginner` | Caixa Iniciante | Primeiros Jobs, ações básicas |
| `intermediate` | Caixa Intermediária | Jobs nível médio, contratos longos |
| `advanced` | Caixa Avançada | Especializações, Raids, Liga |

### Estados de Configuração

- **CONFIGURATION_REQUIRED** — Tier existe mas não tem crate/key vinculada
- **Vinculado** — Tier configurado com crate/key reais existentes
- **Desativado** — Tier desligado na configuração

### Como Vincular um Tier a uma Crate Real

1. Crie a crate e a chave no módulo de Crates (`/crates`)
2. Abra o Jobs Editor: `/jobsadmin editor`
3. Clique em "Tiers de Crate" ou use `/jobsadmin editor tiers`
4. Clique no tier desejado (Iniciante, Intermediária, Avançada)
5. Clique na crate desejada na lista de crates disponíveis
6. Ou use comando: `/jobsadmin editor tiers vincular <tierId> <crateId> <keyId>`

### Regras

- Crate inexistente NÃO pode ser vinculada
- Tier sem vínculo não concede chaves
- Jobs continuam funcionando para XP/Coins/Fragmentos sem crate vinculada
- O sistema não cria crates automaticamente

## Fluxo de Edição

### Abrir Editor
```
/jobsadmin editor
```

### Editar um Job
1. Abra o dashboard principal
2. Clique no Job desejado
3. O sistema cria um rascunho (não afeta produção)
4. Edite campos:
   - Identidade (nome, descrição)
   - Status (ativar/desativar)
   - Recompensas (Coins, XP, Fragmentos, Chaves, Itens)
   - Crates e Tiers
   - Permissões
   - Contratos
5. Valide: clique em "Validar"
6. Publique: clique em "Publicar"
7. O sistema revalida, salva, registra auditoria

### Simular Recompensa
1. No editor do Job, clique em "Simular Recompensa"
2. Ou use `/jobsadmin editor simulate <jobId>`
3. Selecione um Job e uma ação
4. O sistema mostra previsão de XP, Coins, Fragmentos, Chaves
5. NENHUMA recompensa real é concedida

### Histórico e Rollback
1. Clique em "Histórico" no dashboard
2. Ou `/jobsadmin editor revisions`
3. Veja todas as revisões publicadas
4. Clique em uma revisão para ver detalhes
5. Clique novamente para reverter (rollback)

## Permissões Administrativas

| Permissão | Descrição |
|-----------|-----------|
| `jobs.admin.editor.open` | Abrir o editor |
| `jobs.admin.editor.edit` | Criar/editar rascunhos |
| `jobs.admin.editor.publish` | Publicar configurações |
| `jobs.admin.editor.rollback` | Executar rollback |
| `jobs.admin.editor.jobs` | Gerenciar Jobs |
| `jobs.admin.editor.rewards` | Gerenciar recompensas |
| `jobs.admin.editor.crates` | Gerenciar vínculos de crate |
| `jobs.admin.editor.contracts` | Gerenciar contratos |
| `jobs.admin.editor.integrations` | Ver integrações |
| `jobs.admin.editor.permissions` | Gerenciar permissões |
| `jobs.admin.editor.simulate` | Simular recompensas |
| `jobs.admin.editor.reload` | Recarregar configuração |

### Bypass Permissions (Apenas Admin)

| Permissão | Descrição |
|-----------|-----------|
| `bigbangessentials.jobs.bypass.rank` | Bypass de requisito de Rank |
| `bigbangessentials.jobs.bypass.license` | Bypass de requisito de licença |
| `bigbangessentials.jobs.bypass.slot` | Bypass de limite de slot |
| `bigbangessentials.jobs.bypass.cooldown` | Bypass de cooldown |
| `bigbangessentials.jobs.bypass.integration` | Bypass de integração |

## Integrações Cobbleverse

O painel de integrações mostra em tempo real:

- Estado de cada bridge (ACTIVE/DEGRADED/ERROR/DISABLED)
- Versão do mod detectado
- Ações suportadas
- Ações indisponíveis
- Jobs que dependem de cada integração

Para abrir: `/jobsadmin editor integrations` ou clique no dashboard.

## Passo a Passo para Ações Comuns

### Alterar Coins de um Job
1. `/jobsadmin editor`
2. Clique no Job
3. Clique em "Recompensas"
4. Clique em "Coins"
5. Ajuste o valor
6. Valide e publique

### Vincular uma Crate
1. Crie a crate no módulo de Crates
2. `/jobsadmin editor tiers`
3. Clique no tier
4. Clique na crate desejada
5. Confirme

### Adicionar Item Reward
1. `/jobsadmin editor`
2. Clique no Job → Recompensas → Itens Diretos
3. Configure item_id, quantidade, NBT (se suportado)

### Desabilitar Job Temporariamente
1. No editor do Job, clique em "Status"
2. Alterna entre Ativo/Inativo
3. Publique

### Testar Mudança Antes de Publicar
1. Edite o rascunho
2. Clique em "Simular"
3. Verifique estimativas
4. Publique ou descarte

## Validação e Segurança

- Configuração inválida NÃO publica
- Rascunhos são isolados por administrador
- Edição concorrente é detectada
- Rollback não apaga dados de jogadores
- Auditoria registra toda publicação e rollback
- Simulação não concede recompensa real

## Comandos

```
/jobsadmin editor              — Abrir dashboard principal
/jobsadmin editor tiers        — Gerenciar tiers de crate
/jobsadmin editor integrations — Painel de integrações
/jobsadmin editor revisions    — Histórico de revisões
/jobsadmin editor simulate <jobId> — Simular recompensa
/jobsadmin reload              — Recarregar configuração
/jobsadmin diag                — Diagnóstico do pipeline
/jobsadmin integrations        — Status das integrações (texto)
```
