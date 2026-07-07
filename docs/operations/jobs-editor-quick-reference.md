# Jobs Editor — Guia de Referência Rápida

## Comandos Essenciais

```bash
/jobsadmin editor              # Abrir editor visual
/jobsadmin editor tiers        # Configurar crate tiers
/jobsadmin editor integrations # Ver estado das bridges
/jobsadmin editor revisions    # Histórico e rollback
/jobsadmin editor simulate <jobId> # Simular sem conceder
/jobsadmin reload              # Recarregar configs
/jobsadmin diag                # Pipeline stats
/jobsadmin integrations        # Integrations (texto)
```

## Permissões

| Nível | Permissão |
|-------|-----------|
| Ver editor | `jobs.admin.editor.open` |
| Editar rascunhos | `jobs.admin.editor.edit` |
| Publicar | `jobs.admin.editor.publish` |
| Gerenciar crates | `jobs.admin.editor.crates` |
| Gerenciar permissões | `jobs.admin.editor.permissions` |
| Simular | `jobs.admin.editor.simulate` |
| Rollback | `jobs.admin.editor.rollback` |
| Recarregar | `jobs.admin.editor.reload` |

## Passo a Passo Rápido

### Alterar Coins de um Job
1. `/jobsadmin editor`
2. Clique no Job → Recompensas → Coins
3. Ajuste o valor → Salvar Rascunho → Validar → Publicar

### Alterar XP de um Job
1. `/jobsadmin editor`
2. Clique no Job → Recompensas → XP
3. Ajuste o valor → Validar → Publicar

### Alterar Quantidade de Item
1. `/jobsadmin editor`
2. Clique no Job → Recompensas → Itens Diretos
3. Ajuste item_id e quantidade → Validar → Publicar

### Vincular Crate a um Tier
1. Crie crate/key no módulo de Crates
2. `/jobsadmin editor tiers`
3. Clique no tier → Selecione crate → Confirmar

### Criar Regra de Chave
1. Edite o Job → Crates e Tiers
2. Configure keyChance, keyMaxPerDay, keyCooldown
3. Vincule chave existente → Validar → Publicar

### Liberar Job por Permissão
1. Edite o Job → Permissões
2. Defina permissionNode (ex: `jobs.profissao.miner`)
3. Escolha o modo (ALL_REQUIREMENTS recomendado)
4. Publicar

### Desabilitar Job
1. Edite o Job → Status → Alternar para Inativo
2. Publicar
3. Níveis e licenças dos jogadores são preservados

### Testar Antes de Publicar
1. Edite o rascunho
2. `/jobsadmin editor simulate <jobId>`
3. Verifique estimativas de XP, Coins, Fragmentos, Chaves
4. Publicar se ok; Descartar se não

### Rollback de Configuração
1. `/jobsadmin editor revisions`
2. Encontre a revisão alvo
3. Clique para reverter
4. Dados de jogadores NÃO são afetados

### Verificar Integrações Cobbleverse
1. `/jobsadmin editor integrations`
2. Cada bridge mostra: estado, mod, versão, ações
3. Jobs dependentes listados em cada bridge

## Estrutura de Tiers

```
Tier BEGINNER → Caixa Iniciante (primeiros Jobs)
Tier INTERMEDIATE → Caixa Intermediária (Jobs nível médio)
Tier ADVANCED → Caixa Avançada (especializações)
```

Cada tier pode ser vinculado a qualquer crate/key real do sistema.

## Estados de Job

- **AVAILABLE** — Job ativo e funcional
- **DISABLED_BY_CONFIG** — Job desativado
- **CONFIGURATION_REQUIRED** — Tier de crate sem vínculo
- **INTEGRATION_MISSING** — Mod/bridge necessário ausente
- **BRIDGE_DEGRADED** — Bridge ativa mas com funcionalidade reduzida
- **BRIDGE_ERROR** — Bridge com erro

## Regras Importantes

1. Rascunho NÃO afeta produção
2. Publicação inválida é bloqueada
3. Rollback preserva dados de jogadores
4. Edições concorrentes são detectadas
5. Simulação NÃO concede recompensa real
6. Tier sem vínculo NÃO concede chaves
7. Crate inexistente NÃO pode ser vinculada
