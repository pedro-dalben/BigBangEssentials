# Configuração

Ative com `modules.pokemarketEnabled: true` em `config.json` ou no arquivo dividido de módulos. O módulo permanece bloqueado sem Cobblemon.

## Configuração do PokéMarket (pokemarket.json)

O PokéMarket permite controlar limites de anúncios ativos por jogador e tabela de preços mínimos globais/dinâmicos com base nos atributos do Pokémon para prevenir inflação e desvalorização no ecossistema.

```json
{
  "_configVersion": 3,
  "maxActiveListings": 5,
  "saleTaxPercentage": 5.0,
  "price": {
    "min": 0.01,
    "max": 1000000.00,
    "minLegendary": 100.00,
    "minMythical": 100.00,
    "minUltraBeast": 50.00,
    "minShiny": 50.00,
    "minByPerfectIvs": {
      "0": 0.01,
      "1": 0.01,
      "2": 0.01,
      "3": 0.01,
      "4": 0.01,
      "5": 10.00,
      "6": 20.00
    },
    "minBySpecies": {
      "mewtwo": 500.00,
      "rayquaza": 500.00
    }
  },
  "recovery": { "reservedTimeoutMinutes": 5 }
}
```

### Parâmetros Principais
- **`maxActiveListings`**: Limite padrão de anúncios ativos por jogador (vendas + trocas). Valor `-1` indica anúncios ilimitados.
- **`saleTaxPercentage`**: Porcentagem de taxa cobrada nas vendas por dinheiro (padrão `5.0`).
- **`price.min` / `price.max`**: Limites globais absolutos de preço.
- **`price.minByPerfectIvs`**: Mapeamento do número de IVs perfeitos (31) para o preço mínimo (ex: F5 = 10, F6 = 20).
- **`price.minLegendary` / `price.minMythical` / `price.minUltraBeast` / `price.minShiny`**: Preço mínimo por raridade e status shiny.
- **`price.minBySpecies`**: Preço mínimo por espécie específica (chave em minúsculas).

*Nota: O sistema sempre aplica o maior valor entre o mínimo global e as regras dinâmicas ativas para o Pokémon.*

Dependências de desenvolvimento: Cobblemon é resolvido do Modrinth Maven (`maven.modrinth:cobblemon`) via propriedades definidas em `gradle.properties`. São `compileOnly`/`modCompileOnly` e não entram no JAR do BigBangEssentials. O servidor fornece Cobblemon separadamente em runtime.
