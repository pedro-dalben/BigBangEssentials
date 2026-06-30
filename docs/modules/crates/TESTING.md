# Testes do Módulo de Crates

## Executando Testes

Para executar os testes do módulo de crates:

```bash
# Testes do módulo completo
./gradlew test

# Testes específicos do módulo de crates (se houver classe de teste dedicada)
./gradlew test --tests "*Crate*"
```

## Cobertura de Testes

As classes de domínio (`CrateDefinition`, `KeyDefinition`, `CrateReward`, `CrateRarity`, `CrateMilestone`, `CrateLocation`, `PlayerVirtualKeyBalance`, `PlayerCrateState`, `RewardRollState`, `CrateOpenAudit`, `ItemSerializer`, `CrateAnimationConfig`, `CratePreviewConfig`, `CrateVisualConfig`, `CrateParticleConfig`, `CrateRequirements`) são altamente testáveis por serem POJOs com lógica de validação e serialização JSON.

## Cenários de Teste Manual

### Teste 1: Criação e Configuração de Crate

```bash
# 1. Criar uma crate
/crate create teste_crate "Crate de Teste"

# 2. Verificar se apareceu no editor
/crate editor

# 3. Adicionar raridades
/crate addrarity teste_crate comum "Comum" "#AAAAAA" 50
/crate addrarity teste_crate raro "Raro" "#FFD700" 30
/crate addrarity teste_crate lendario "Lendario" "#FF0000" 20

# 4. Adicionar recompensas
/crate reward create teste_crate pedra "Bloco de Pedra" comum
/crate reward create teste_crate dinheiro "Moedas" raro

# 4.1. Testar metadados avançados da recompensa
/crate reward settype teste_crate pedra ITEM
/crate reward setlore teste_crate pedra "Linha 1 | Linha 2"
/crate reward setperm teste_crate pedra clear
/crate reward setvisible teste_crate pedra true
/crate reward setdisplayorder teste_crate pedra 5

# 5. Verificar preview
/crate preview teste_crate

# 6. Salvar e recarregar
/crate reload
```

### Teste 2: Sistema de Chaves

```bash
# 1. Criar chave
/crate key create chave_teste "Chave de Teste"

# 2. Vincular à crate
/crate key addcrate chave_teste teste_crate

# 3. Definir chave como requisito da crate (via editor ou comando)
# No editor, seção "Requisitos"

# 4. Dar chave a um jogador
/givekey chave_teste jogador 5

# 5. Verificar saldo
/crate key inspect jogador

# 6. Tentar abrir sem chave (deve falhar)
/crate open teste_crate

# 7. Dar chave e abrir
/givekey chave_teste jogador 1
/crate open teste_crate
```

### Teste 3: Cooldown

```bash
# 1. Configurar cooldown de 30 segundos
/crate setcooldown teste_crate 30000

# 2. Abrir a crate
/crate open teste_crate

# 3. Tentar abrir imediatamente (deve mostrar mensagem de cooldown)
/crate open teste_crate

# 4. Resetar cooldown
/crate resetcooldown jogador teste_crate

# 5. Abrir novamente (deve funcionar)
/crate open teste_crate
```

### Teste 4: Bloco no Mundo

```bash
# 1. Posicionar um bloco no mundo
# (coloque um bloco qualquer)

# 2. Vincular a crate ao bloco
/crate setlocation teste_crate
# Siga as instruções na tela

# 3. Verificar holograma e partículas
# O holograma deve aparecer acima do bloco
# Partículas devem aparecer ao redor

# 4. Clicar com botão direito no bloco
# Deve abrir a crate (se tiver chave)

# 5. Shift + clique no bloco
# Deve abrir o preview

# 6. Verificar localizações
/crate location list
```

### Teste 5: Limites de Recompensa

```bash
# 1. Configurar recompensa com playerLimit = 1 (via editor ou JSON)
# No JSON: "playerLimit": 1

# 2. Abrir a crate e ganhar a recompensa

# 3. Tentar ganhar novamente — a recompensa não deve ser selecionável
# até que todas as outras recompensas elegíveis se esgotem

# 4. Repetir com globalLimit
```

### Teste 6: Milestones

```bash
# 1. Criar milestone na crate
/crate addmilestone teste_crate marco10 "10 Aberturas" pedra 10

# 2. Abrir a crate 10 vezes

# 3. Verificar se a recompensa do milestone foi entregue
```

### Teste 7: Custo Econômico

```bash
# 1. Configurar custo de 50 moedas na crate
/crate setcost teste_crate 50

# 2. Verificar saldo
/balance

# 3. Abrir a crate
/crate open teste_crate

# 4. Verificar se o saldo foi debitado
/balance
```

### Teste 8: Permissões

```bash
# 1. Testar comando sem permissão (como jogador sem OP)
/crate editor
# Deve mostrar: "Você não tem permissão para isso."

# 2. Dar permissão ao jogador
# /permissions user jogador add bigbangessentials.crates.editor

# 3. Testar novamente
/crate editor
# Deve funcionar
```

### Teste 9: Idempotência

```bash
# 1. Abrir uma crate
/crate open teste_crate

# 2. Verificar logs — deve ter exatamente 1 entrada de COMPLETED
/crate logs jogador teste_crate

# 3. Tentar intervalo rápido de cliques (simular race condition)
# O sistema deve detectar a idempotência e não criar duplicatas
```

### Teste 10: Abertura em Massa

```bash
# 1. Dar várias chaves
/givekey chave_teste jogador 100

# 2. Abrir em massa
/crate massopen teste_crate 10
```

### Teste 11: Coleta de Entregas Pendentes

```bash
# 1. Encher o inventário do jogador
# (deixe o inventário cheio com itens comuns)

# 2. Abrir uma crate que entregue um item ao inventário
/crate open teste_crate

# 3. Se o inventário estiver cheio, o item deve ir para a caixa de entregas
# 4. Resgatar os itens pendentes
/crates claim
```

### Teste 12: Proteção de Bloco

```bash
# 1. Jogador sem permissão tenta quebrar bloco da crate
# Deve ser impedido

# 2. Admin tenta quebrar (deve remover a localização)
# Verificar se foi removida: /crate location list

# 3. Explosão próxima ao bloco
# Bloco deve permanecer intacto
```

### Teste 13: Persistência

```bash
# 1. Criar várias crates, chaves e localizações
# 2. Reiniciar o servidor
# 3. Verificar se tudo foi carregado:
/crate editor
/crate location list
/crate key inspect jogador
```

## Casos de Teste Comuns (Unitários Esperados)

### Teste de CrateDefinition

- Criar com key válida → sucesso
- Criar com key inválida (maiúsculas, espaços, caracteres especiais) → exceção
- Criar com key null → exceção
- Calcular chance de raridade com raridades ativas/inativas
- Calcular chance de recompensa
- Filtrar recompensas elegíveis
- Serializar e desserializar JSON → roundtrip idêntico

### Teste de KeyDefinition

- Criar com ID válido → sucesso
- Criar com ID inválido → exceção
- Alternar virtual/physical
- Serializar e desserializar JSON

### Teste de CrateReward

- Verificar elegibilidade com permissões
- Verificar elegibilidade com limites (global/player)
- Verificar elegibilidade com blockingPermissions
- Recompensa inativa não é elegível

### Teste de PlayerCrateState

- Cooldown ativo quando timestamp futuro
- Cooldown expirado quando timestamp passado
- RecordOpening incrementa totalOpened
- Verificação de milestone alcançado/não alcançado
- Milestone repetível (a cada N)

### Teste de RewardService

- Seleção de raridade por peso (múltiplas rodadas)
- Seleção de recompensa por peso
- Seleção falha quando não há recompensas ativas
- Seleção respeita milestoneOnly

### Teste de CrateOpeningService

- Fluxo completo de abertura bem-sucedido
- Bloqueio por crate desabilitada
- Bloqueio por cooldown
- Bloqueio por falta de chave
- Bloqueio por falta de fundos
- Bloqueio por crate sem recompensas
- Detecção de idempotência
- Mass open com sucesso parcial
- Entrega de milestones

### Teste de CrateKeyService

- Dar chave virtual → saldo incrementa
- Retirar chave virtual → saldo decrementa
- Retirar sem saldo → retorna false
- Definir saldo
- Consumir chave para abertura
- Verificar se tem chave requerida

### Teste de ItemSerializer

- Serializar ItemStack → JSON
- Desserializar JSON → ItemStack
- Roundtrip com componentes
- Item vazio → `{"empty": true}`

### Teste de CrateHologramManager

- Spawn de holograma (cria ArmorStands)
- Remoção de holograma
- Atualização de conteúdo
- Remoção em massa

### Teste de CrateParticleManager

- Iniciar partículas idle
- Parar partículas
- Tick com jogador próximo/distante
- Diferentes formatos (CIRCLE, SPIRAL, COLUMN, AURA)
