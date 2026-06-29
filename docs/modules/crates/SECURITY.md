# Segurança do Módulo de Crates

## 1. Chaves de Idempotência

Cada operação de abertura de crate recebe uma chave de idempotência única. Antes de processar uma abertura, o `CrateOpeningService` verifica se a chave já foi processada consultando o `CrateAuditService.findByIdempotencyKey()`.

**Funcionamento:**
- Chaves são geradas no formato: `<prefixo>:<playerUUID>:<crateId>:<timestamp>:<índice>`
- Se um log com a mesma chave já existir no banco, a operação é ignorada
- Isso previne duplicação em casos de:
  - Cliques duplicados do jogador
  - Timeout e retry do servidor
  - Processamento paralelo

**Exemplo de chave:** `open:550e8400:minha_crate:1710800000000:0`

## 2. Operações Atômicas de Abertura

O fluxo de abertura (`CrateOpeningService.openCrate()`) é executado em um único encadeamento de operações sequenciais:

1. ✅ Verificação de idempotência
2. ✅ Validação de requisitos (permissão, cooldown, chaves, custo)
3. ✅ Criação de log PENDING
4. ✅ Cálculo da recompensa
5. ✅ Consumo de chave
6. ✅ Cobrança do custo econômico
7. ✅ Aplicação de cooldown
8. ✅ Registro de abertura no estado do jogador
9. ✅ Entrega da recompensa
10. ✅ Verificação e entrega de milestones
11. ✅ Finalização do log como COMPLETED

Se qualquer passo falhar, o log é marcado como FAILED com o detalhe do erro.

## 3. Prevenção de Duplicação

| Mecanismo | Descrição |
|-----------|-----------|
| Idempotência | Chave única por operação impede re-processamento |
| Cooldown | Impede aberturas repetidas dentro do período configurado |
| OneTimeUse | Marca a crate como uso único (cooldown infinito) |
| Consumo de chave | Chave é consumida antes da entrega da recompensa |
| Estado PENDING | Log é criado como pendente antes do consumo |

## 4. Validação de Chaves (Metadados de Chave Física)

Chaves físicas são validadas através da `KeyDefinition`:

- O item físico da chave é verificado contra a definição registrada
- A crate deve estar na lista `compatibleCrateIds` da chave
- Se a chave exigir permissão (`requiredPermission`), ela é verificada antes do uso
- O `CrateKeyService.consumeKeyForOpening()` tenta consumir chaves virtuais primeiro; se `requirePhysicalKey` estiver ativo, o jogador precisa ter o item físico no inventário

## 5. Verificações de Permissão

Todas as verificações usam dois níveis:

1. **Nível de OP**: `source.hasPermission(4)` — bypassa qualquer verificação
2. **PermissionAPI**: Para jogadores sem OP, a permissão é verificada via `PermissionAPI.hasPermission(playerUUID, node)`

**Verificações realizadas:**
- Permissão do comando (ex: `bigbangessentials.crates.editor`)
- Permissão da crate (`requiredPermission` em `CrateRequirements`)
- Permissão da chave (`requiredPermission` em `KeyDefinition`)
- Permissão da recompensa (`requiredPermission` em `CrateReward`)
- Permissão de bloqueio (`blockingPermissions` em `CrateReward`)

## 6. Proteção de Blocos

O `CrateBlockListener` protege blocos vinculados a crates:

### Quebra de Bloco (`onBlockBreak`)
- Jogadores sem permissão de administrador (nível 2+) têm a quebra cancelada
- Mensagem de erro é exibida
- Admin com permissão que quebra o bloco automaticamente remove a localização

### Explosões (`onExplosion`)
- Blocos de crate são removidos da lista de blocos afetados
- Explosões de Creeper, TNT, etc. não danificam blocos de crate

### Interação com Bloco (`onBlockInteract`)
- Interação é cancelada se a crate não existir ou estiver desabilitada
- Se o jogador estiver em uma animação, a interação é bloqueada
- Shift + clique abre o preview em vez de abrir a crate

## 7. Logs de Auditoria

Toda abertura de crate gera um log de auditoria completo (`CrateOpenAudit`) com:

| Campo | Descrição |
|-------|-----------|
| `id` | UUID único do log |
| `playerId` | UUID do jogador |
| `crateId` | ID da crate |
| `keyId` | ID da chave usada |
| `source` | Fonte da abertura (GrantSource) |
| `rewardIds` | IDs das recompensas |
| `rewardNames` | Nomes das recompensas |
| `status` | Status: PENDING → COMPLETED/FAILED |
| `costConsumed` | Custo consumido |
| `timestamp` | Momento da abertura |
| `idempotencyKey` | Chave de idempotência |
| `serverId` | ID do servidor |
| `errorDetail` | Detalhe do erro (se houver) |

### Limpeza de Logs

O método `CrateAuditService.cleanOldAudits(Instant cutoff)` permite limpar logs antigos. Pode ser chamado periodicamente para evitar acúmulo no banco.

## 8. Rate Limiting (Cooldown)

- Cada crate define seu próprio cooldown (`cooldownMillis`)
- O cooldown é verificado pelo `PlayerCrateState.isOnCooldown()`
- O estado é persistido no banco JDBC
- O comando `/crate resetcooldown <jogador> <crate>` permite resetar por admin
- A permissão `bigbangessentials.crates.bypass.cooldown` permite ignorar cooldown

## 9. Integridade de Dados

- **Transações no banco**: Operações de save/update usam o padrão "upsert" (UPDATE, se 0 linhas afetadas → INSERT)
- **Cache de definições**: `JsonCrateRepository` mantém cache em memória, atualizado no reload
- **Fallback em economia**: Se o `EconomyService` não estiver disponível, custos econômicos são ignorados
- **Overflow de inventário**: Itens que não cabem no inventário são dropados no chão
