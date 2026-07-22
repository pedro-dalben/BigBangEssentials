# AdminShop

Loja virtual administrativa independente do ChestShop físico de jogadores.

## Acesso

| Comando | Loja | Moeda |
|---|---|---|
| `/shop` | Admin Shop | money |
| `/cash` | Cash Shop | gems |
| `/gemas shop` | Alias do Cash Shop | gems |
| `/adminshop reload` | Recarrega configuração | administração |

`/adminshop reload` exige nível 2 do Minecraft. O módulo pode ser desativado
com `modules.json > adminshopEnabled: false` e passa a valer no próximo
reinício; isso remove os comandos do AdminShop, sem afetar o ChestShop nem
remover configuração, estado ou tabelas SQL.

## Arquivos

| Arquivo | Uso |
|---|---|
| `world/serverconfig/bigbangessentials/adminshop.json` | Catálogo e regras |
| `bigbangessentials/adminshop_state.json` | Fallback/migração local |
| banco de `config/database.json` | Estado principal quando SQL está disponível |
| `world/serverconfig/bigbangessentials/menus/adminshop_*_menu.yml` | Layout |

Na primeira inicialização, o JSON e os menus são criados com um catálogo
vanilla inicial. Edite o JSON e use `/adminshop reload`.

## Catálogo

Cada loja é uma entrada em `stores`. Os IDs de produto devem ser únicos.

```json
{
  "stores": {
    "money": {
      "currency": "money",
      "products": [
        {
          "id": "diamond",
          "displayName": "Diamante",
          "itemId": "minecraft:diamond",
          "quantity": 1,
          "buyPrice": 100,
          "sellPrice": 25,
          "buyEnabled": true,
          "sellEnabled": true,
          "stock": -1,
          "limit": -1,
          "permission": "bigbangessentials.adminshop.diamond",
          "page": 1,
          "slot": 13
        }
      ]
    },
    "gems": { "currency": "gems", "products": [] }
  }
}
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

O serviço serializa operações e valida produto, permissão, preço, limite,
estoque, saldo e inventário antes de alterar dados.

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

O campo opcional `permission` do produto continua sendo uma restrição adicional.
Todas são verificadas por `PermissionAPI.hasPermission`, funcionando com o sistema
interno, LuckPerms, FTB Ranks e adaptadores configurados.

## Limites do v1

Não há editor gráfico, leilão, venda rápida, sincronização entre servidores ou
preços baseados em jogadores online. Esses recursos devem permanecer separados
do motor de transação e do ChestShop.
