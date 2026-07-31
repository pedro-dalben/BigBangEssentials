# AdminShop

Loja virtual administrativa independente do ChestShop físico de jogadores.

## Acesso

| Comando | Loja | Moeda |
|---|---|---|
| `/shop` | Admin Shop | money |
| `/cash` | Cash Shop | gems |
| `/gemas shop` | Alias do Cash Shop | gems |
| `/adminshop reload` | Recarrega configuração | administração |

`/adminshop reload` exige OP nível 2 ou `bigbangessentials.adminshop.admin`. O módulo pode ser desativado
com `modules.json > adminshopEnabled: false` e passa a valer no próximo
reinício; isso remove os comandos do AdminShop, sem afetar o ChestShop nem
remover configuração, estado ou tabelas SQL.

## Arquivos

| Arquivo | Uso |
|---|---|
| `world/serverconfig/bigbangessentials/shops/<loja>/<categoria>.yml` | Catálogo editável, um arquivo por categoria |
| `world/serverconfig/bigbangessentials/adminshop.yml` | Compatibilidade e migração |
| `bigbangessentials/adminshop_state.json` | Fallback/migração local |
| banco de `config/database.json` | Estado principal quando SQL está disponível |
| `world/serverconfig/bigbangessentials/menus/adminshop_*_menu.yml` | Layout |

Na primeira inicialização, o catálogo é dividido automaticamente em arquivos
YAML por categoria. Edite, por exemplo,
`shops/money/blocks.yml` e use `/adminshop reload`.

Para criar uma categoria, copie um arquivo de categoria para
`shops/money/<nova-categoria>.yml`, ajuste `category`, `title`, `icon`, `order`
e `products`, e use `/adminshop reload`. Para alterar somente o preço:

```yaml
products:
  blocks_1:
    price:
      buy: 100
```

Também é possível editar pelo servidor:

| Comando | Ação |
|---|---|
| `/adminshop category list <loja>` | Lista categorias e quantidade de itens |
| `/adminshop category create <loja> <id> <título>` | Cria uma categoria |
| `/adminshop category delete <loja> <id>` | Remove categoria vazia |
| `/adminshop item setprice <id> buy\|sell <valor>` | Altera preço de um item |
| `/adminshop item addhand <loja> <categoria> <id>` | Adiciona o item da mão |
| `/adminshop item remove <id>` | Remove um item |

Os IDs de loja aceitam maiúsculas/minúsculas e também a moeda (`money` ou
`gems`).

`/shop` é exclusivamente AdminShop; não é alias do ChestShop. As ações do menu
usam uma saga assíncrona: SQL/economia não bloqueiam o servidor, enquanto
inventário e comandos retornam ao thread do servidor. Produtos `command` devem
conter `{transaction}`; uma entrega ambígua fica visível para reconciliação.

## Catálogo

Cada loja é uma entrada em `stores`. Os IDs de produto devem ser únicos.

```yaml
# shops/money/blocks.yml
store: money
category: blocks
title: "§aBlocos"
icon: "minecraft:grass_block"
order: 10
products:
  diamond:
    store: money
    category: blocks
    displayName: Diamante
    itemId: "minecraft:diamond"
    quantity: { defaultQuantity: 1, options: [1, 16, 64], max: 64 }
    price: { buy: 100, sell: 25 }
```

`stock: -1` e `limit: -1` significam ilimitado. `command` cria um produto
entregue pelo comando do servidor e esses produtos nunca podem ser vendidos.
Use `item` com o formato do `ItemSerializer` para preservar componentes
customizados; `itemId` basta para itens vanilla.

## Preços dinâmicos

Ative por produto:

```json
"dynamic": {
  "enabled": true,
  "step": 0.05,
  "minMultiplier": 0.50,
  "maxMultiplier": 2.00
}
```

O preço atual é `preço-base × clamp(1 + step × demanda, mínimo, máximo)`.
Compra aumenta a demanda e venda reduz. A demanda fica limitada entre menos
1000 e mais 1000, é persistida em SQL e o menu mostra o preço vigente. O valor
é recalculado e validado no servidor no momento da transação.

## Transações

O serviço valida produto, permissão, preço, limite, estoque, saldo e inventário
antes de reservar estado. A operação financeira e o journal usam chaves
idempotentes; falha de entrega, SQL ou compensação termina em rollback confirmado
ou `RECONCILIATION_REQUIRED`.

- money usa `EconomyManager`;
- gems usa reserva/captura e restauração do saldo original em rollback;
- itens são comparados com componentes, não pelo nome exibido;
- IDs e uma janela curta contra clique duplo evitam duplicações;
- falha de entrega ou persistência restaura item, moeda, demanda, limite e
  estoque.

## SQL

A migração `V015CreateAdminShopTables` cria:

| Tabela | Conteúdo |
|---|---|
| `adminshop_state` | Estoque restante por produto |
| `adminshop_limits` | Uso do limite por jogador/produto |
| `adminshop_demand` | Demanda dos preços dinâmicos |
| `adminshop_transactions` | Auditoria de transações concluídas |

SQLite e MySQL usam o `DatabaseManager` e suas migrações normais. Se o banco
estiver indisponível, o módulo usa JSON; quando o SQL estiver vazio, o estado
JSON existente é importado para as tabelas.

## Permissões

As permissões da moeda são obrigatórias tanto para abrir quanto para transacionar
(inclusive em menus já abertos):

| Permissão | Acesso |
|---|---|
| `bigbangessentials.adminshop.money` | `/shop` e produtos de money |
| `bigbangessentials.adminshop.gems` | `/cash`, `/gemas shop` e produtos de gems |
| `bigbangessentials.adminshop.admin` | `/adminshop reload` e administração |
| `bigbangessentials.adminshop.audit` | Inspeção de transações AdminShop |

O campo opcional `permission` do produto continua sendo uma restrição adicional.
Todas são verificadas por `PermissionAPI.hasPermission`, funcionando com o sistema
interno, LuckPerms, FTB Ranks e adaptadores configurados.

## Limites do v1

Não há editor gráfico, leilão, venda rápida, sincronização entre servidores ou
preços baseados em jogadores online. Esses recursos devem permanecer separados
do motor de transação e do ChestShop.
