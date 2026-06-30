# Editor de Crates — Guia do Administrador

## Acessando o Editor

Use o comando:

```bash
/crate editor
```

Permissão necessária: `bigbangessentials.crates.editor`

## Menu Principal do Editor

O editor principal (`CrateMainEditorMenu`) exibe todas as crates cadastradas em formato de lista paginada (até 35 itens por página). Cada crate é exibida com:

- Ícone configurado (ou um baú padrão)
- Nome e status (ativado/desativado)
- ID técnico
- Descrição (truncada)
- Número de recompensas e raridades

### Botões do Menu Principal

| Botão | Função |
|-------|--------|
| Seta para Esquerda | Página anterior |
| Seta para Direita | Próxima página |
| Bloco de Esmeralda | **Criar Crate** — Abre instruções para criar via comando |
| Alavanca | **Gerenciar Chaves** — Abre o editor de chaves |
| Frasco de Experiência | **Gerenciar Raridades** — Abre instruções para raridades |
| Comparador | **Recarregar** — Recarrega todas as crates do disco |
| Barreira | **Fechar** — Fecha o editor |

### Navegação

- **Clique esquerdo** em uma crate → Abre o menu de edição da crate
- **Shift + clique** em uma chave no editor de chaves → Deleta a chave (com confirmação)
- **Shift + clique** em uma recompensa → Deleta a recompensa (com confirmação)

## Criando uma Crate

1. No menu principal, clique no **Bloco de Esmeralda** "Criar Crate"
2. Use o comando exibido:
   ```bash
   /crate create minha_crate "Minha Crate"
   ```
3. A crate aparecerá no editor após criada

### Parâmetros do comando create

- `minha_crate` — ID técnico (apenas letras minúsculas, números, `_` e `-`)
- `"Minha Crate"` — Nome de exibição (com aspas se tiver espaços)

## Editando uma Crate

Clique em uma crate no menu principal para abrir o `CrateEditMenu`. O menu exibe:

### Informações Gerais (Slot 4)

Ícone da crate com informações consolidadas: ID, nome, descrição, tipo de abertura, cooldown, custo, status, quantidade de recompensas/raridades/milestones/locais.

### Seções de Edição

| Slot | Seção | Descrição |
|------|-------|-----------|
| 18 | **Informações Gerais** | Nome, descrição, ícone, status (ativar/desativar) |
| 19 | **Preview** | Configurações de como os jogadores veem a crate |
| 20 | **Configuração de Abertura** | Tipo (NONE/VIRTUAL/PHYSICAL), sons, animação, partículas |
| 21 | **Requisitos** | Chaves aceitas, permissão necessária, custo econômico, cooldown |
| 22 | **Recompensas** | Lista e edita recompensas da crate |
| 23 | **Raridades** | Gerencia raridades da crate |
| 24 | **Milestones** | Recompensas por número de aberturas |
| 25 | **Visual** | Hologramas, partículas (idle/opening), sons |
| 26 | **Locais** | Blocos vinculados no mundo |

### Botões Inferiores

| Slot | Botão | Função |
|------|-------|--------|
| 45 | Voltar | Volta ao menu principal |
| 48 | **Deletar Crate** | Abre confirmação antes de deletar |
| 49 | **Salvar** | Salva a crate atual |
| 50 | **Toggle Ativo/Inativo** | Alterna entre ativado/desativado |
| 51 | **Visualizar Preview** | Mostra o preview como os jogadores veem |
| 53 | Fechar | Fecha o editor |

### Comandos Rápidos para Edição

Ao clicar em cada seção, o menu exibe comandos úteis:

```bash
# Informações Gerais
/crate setname <crate> <nome>
/crate setdesc <crate> <descrição>
/crate toggle <crate>
/crate seticon <crate>

# Tipo de Abertura
/crate setopening <crate> <NONE|VIRTUAL|PHYSICAL>

# Requisitos
/crate setkey <crate> <keyId>
/crate setcost <crate> <valor>
/crate setcooldown <crate> <ms>
/crate setperm <crate> <permissão>

# Raridades
/crate addrarity <crate> <id> <nome> <cor> <peso>
/crate removerarity <crate> <id>
/crate rarity setname <crate> <rarityId> <nome>
/crate rarity setcolor <crate> <rarityId> <cor>
/crate rarity setweight <crate> <rarityId> <peso>
/crate rarity seticon <crate> <rarityId>
/crate rarity setlore <crate> <rarityId> <linha1 | linha2>
/crate rarity toggle <crate> <rarityId>
/crate rarity setpriority <crate> <rarityId> <prioridade>
/crate rarity setdisplayorder <crate> <rarityId> <ordem>

# Milestones
/crate addmilestone <crate> <id> <nome> <rewardId> <aberturas>

# Locais
/crate setlocation <crate>
```

## Gerenciando Chaves

No menu principal, clique em **"Gerenciar Chaves"** (slot 50) ou use `/crate key editor`.

O `CrateKeyEditorMenu` exibe:

- Lista paginada de todas as chaves cadastradas
- Para cada chave: nome, ID, tipo (virtual/física), status, crates compatíveis

### Ações

- **Clique esquerdo** → Exibe detalhes e comandos para editar a chave
- **Shift + clique** → Abre confirmação para deletar a chave

### Criar Nova Chave

1. Clique no **Bloco de Esmeralda** "Nova Chave"
2. Use o comando:
   ```bash
   /crate key create <id> <nome>
   ```

### Comandos para Chaves

```bash
/crate key setname <id> <nome>
/crate key settype <id> <virtual|physical>
/crate key toggle <id>
/crate key seticon <id>
/crate key addcrate <id> <crateKey>
```

## Gerenciando Recompensas

No menu de edição da crate, clique em **"Recompensas"** (slot 22) para abrir o `CrateRewardListMenu`.

### Ações

- **Clique esquerdo** → Exibe detalhes e comandos para editar
- **Shift + clique** → Abre confirmação para deletar

### Criar Nova Recompensa

1. Clique no **Bloco de Esmeralda** "Nova Recompensa"
2. Use o comando:
   ```bash
   /crate reward create <crate> <id> <nome> <rarityId>
   ```

### Comandos para Recompensas

```bash
/crate reward setname <crate> <rewardId> <nome>
/crate reward setweight <crate> <rewardId> <peso>
/crate reward setrarity <crate> <rewardId> <rarityId>
/crate reward toggle <crate> <rewardId>
/crate reward seticon <crate> <rewardId>
/crate reward setitems <crate> <rewardId>
/crate reward additem <crate> <rewardId>
/crate reward clearitems <crate> <rewardId>
/crate reward setcommands <crate> <rewardId> <cmd1 | cmd2>
/crate reward addcommand <crate> <rewardId> <comando>
/crate reward clearcommands <crate> <rewardId>
/crate reward settype <crate> <rewardId> <ITEM|COMMAND>
/crate reward setlore <crate> <rewardId> <linha1 | linha2>
/crate reward setperm <crate> <rewardId> <permissão|clear>
/crate reward setvisible <crate> <rewardId> <true|false>
/crate reward setmilestoneonly <crate> <rewardId> <true|false>
/crate reward setbroadcast <crate> <rewardId> <true|false>
/crate reward setbroadcastmsg <crate> <rewardId> <mensagem>
/crate reward setplayermsg <crate> <rewardId> <mensagem>
/crate reward setdisplayorder <crate> <rewardId> <ordem>
/crate reward setgloballimit <crate> <rewardId> <limite>
/crate reward setplayerlimit <crate> <rewardId> <limite>
/crate reward setblockingperms <crate> <rewardId> <perm1 | perm2>
```

## Vinculando Blocos

Para vincular uma crate a um bloco no mundo:

1. No menu de edição, clique em **"Locais"** (slot 26)
2. Use o comando:
   ```bash
   /crate setlocation <crate>
   ```
3. Olhe para o bloco desejado e execute o comando

Para remover um bloco:
```bash
/crate location remove <id>
```

## Configurando Hologramas e Partículas

No menu de edição, clique em **"Visual"** (slot 25) para ver as configurações atuais.

As configurações de holograma são definidas no `CrateVisualConfig`:

- **Linhas do holograma**: Texto com placeholders `{name}`, `{description}`, `{key}`
- **Partículas IDLE**: Tipo de partícula, formato (CIRCLE/SPIRAL/COLUMN/AURA), frequência, raio
- **Partículas OPEN**: Configuração similar durante abertura
- **Sons**: Som de aproximação, som de abertura, raio do som de aproximação
- **Offset Y do holograma**: Altura acima do bloco

### Exemplo de Configuração de Holograma

As linhas padrão são:
```
§6§l{name}
§7{description}
§e§lChaves: §f{key_amount}
§7Clique para abrir
```

### Formatos de Partículas

| Formato | Descrição |
|---------|-----------|
| `CIRCLE` | Partículas em círculo horizontal ao redor do bloco |
| `SPIRAL` | Partículas em espiral ascendente |
| `COLUMN` | Partículas em coluna vertical |
| `AURA` | Partículas aleatórias ao redor do bloco (formato aura) |
| `NONE` | Sem partículas |

## Deletando com Confirmação

Ações destrutivas (deletar crate, deletar chave, deletar recompensa) usam o `CrateConfirmationMenu`:

1. O menu de confirmação exibe:
   - Título da ação (ex: "Deletar Crate")
   - Mensagem de aviso
   - Botão **Sim** (lã verde) → Executa a ação
   - Botão **Não** (lã vermelha) → Cancela

2. Após confirmar:
   - A ação é executada
   - Uma mensagem de sucesso é enviada
   - O menu é atualizado para refletir a mudança

### Ações que Usam Confirmação

- Deletar crate (menu de edição)
- Deletar chave (shift + clique no editor de chaves)
- Deletar recompensa (shift + clique na lista de recompensas)
