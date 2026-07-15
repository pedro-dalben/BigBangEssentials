# Professions Reference

## Common Professions (11)

### 1. Minerador
| Property | Value |
|----------|-------|
| ID | `miner` |
| Display Name | Minerador |
| Category | COMMON |
| Icon | `minecraft:diamond_pickaxe` |
| Description | Extração de minérios e pedras preciosas |
| License Required | No |
| Unlocked By Default | Yes |
| Slot | COMMON_PRIMARY |
| Required Integration | None |
| Daily Limit | Unlimited |
| Max Level | 100 |

**How to earn money**: Break natural ores (coal, copper, iron, gold, redstone, lapis, diamond, emerald, nether quartz, nether gold, ancient debris) and stone blocks. Rare ores like diamond, emerald, and ancient debris pay the most.

**How to earn XP**: Every broken block that pays money also grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| BREAK-BLOCK | `minecraft:coal_ore` | 5 | 10 |
| BREAK-BLOCK | `minecraft:diamond_ore` | 30 | 40 |
| BREAK-BLOCK | `minecraft:ancient_debris` | 100 | 150 |
| BREAK-BLOCK | `minecraft:stone` | 1 | 2 |
| BREAK-BLOCK | `*` (wildcard) | 0.5 | 1 |

**Skills**: None by default (configurable).

---

### 2. Lenhador
| Property | Value |
|----------|-------|
| ID | `woodcutter` |
| Display Name | Lenhador |
| Category | COMMON |
| Icon | `minecraft:diamond_axe` |
| Description | Corte de árvores e coleta de recursos florestais |
| License Required | No |
| Unlocked By Default | Yes |
| Slot | COMMON_PRIMARY |
| Required Integration | None |
| Daily Limit | Unlimited |
| Max Level | 100 |

**How to earn money**: Break log blocks from all tree types (oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, crimson, warped). Nether stems pay more.

**How to earn XP**: Each log broken grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| BREAK-BLOCK | `minecraft:oak_log` | 5 | 10 |
| BREAK-BLOCK | `minecraft:cherry_log` | 6 | 11 |
| BREAK-BLOCK | `minecraft:crimson_stem` | 7 | 14 |
| BREAK-BLOCK | `*` (wildcard) | 1 | 2 |

---

### 3. Fazendeiro
| Property | Value |
|----------|-------|
| ID | `farmer` |
| Display Name | Fazendeiro |
| Category | COMMON |
| Icon | `minecraft:diamond_hoe` |
| Description | Agricultura e pecuária |
| License Required | No |
| Unlocked By Default | Yes |
| Slot | COMMON_PRIMARY |
| Required Integration | None |
| Daily Limit | Unlimited |
| Max Level | 100 |

**How to earn money**: Harvest mature crops (wheat, potatoes, carrots, beetroots, nether wart, pumpkin, melon) and slaughter farm animals (cow, pig, sheep, chicken, rabbit). Nether wart pays more than common crops.

**How to earn XP**: Each harvest or kill grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| HARVEST-CROP | `minecraft:wheat` | 3 | 5 |
| HARVEST-CROP | `minecraft:nether_wart` | 8 | 12 |
| KILL-ENTITY | `minecraft:cow` | 5 | 10 |
| KILL-ENTITY | `minecraft:chicken` | 4 | 8 |

---

### 4. Construtor
| Property | Value |
|----------|-------|
| ID | `builder` |
| Display Name | Construtor |
| Category | COMMON |
| Icon | `minecraft:bricks` |
| Description | Construção e colocação de blocos decorativos e estruturais |
| License Required | No |
| Unlocked By Default | Yes |
| Slot | COMMON_PRIMARY / COMMON_SECONDARY |
| Required Integration | None |
| Daily Limit | Unlimited |
| Max Level | 100 |

**How to earn money**: Place decorative and structural blocks (stone bricks, bricks, planks, polished stones, glass, terracotta, concrete). More elaborate blocks pay more.

**How to earn XP**: Each block placed grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| PLACE-BLOCK | `minecraft:stone_bricks` | 2 | 4 |
| PLACE-BLOCK | `minecraft:polished_andesite` | 3 | 5 |
| PLACE-BLOCK | `minecraft:concrete` | 2 | 4 |
| PLACE-BLOCK | `*` (wildcard) | 0.5 | 1 |

---

### 5. Ferreiro
| Property | Value |
|----------|-------|
| ID | `blacksmith` |
| Display Name | Ferreiro |
| Category | COMMON |
| Icon | `minecraft:anvil` |
| Description | Fundição de minérios e criação de itens metálicos |
| License Required | No |
| Unlocked By Default | Yes |
| Slot | COMMON_PRIMARY / COMMON_SECONDARY |
| Required Integration | None |
| Daily Limit | Unlimited |
| Max Level | 100 |

**How to earn money**: Smelt ores into ingots (iron, gold, copper, netherite). Netherite ingots pay dramatically more.

**How to earn XP**: Each smelted item grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| SMELT-ITEM | `minecraft:iron_ingot` | 3 | 6 |
| SMELT-ITEM | `minecraft:gold_ingot` | 5 | 10 |
| SMELT-ITEM | `minecraft:netherite_ingot` | 50 | 100 |
| SMELT-ITEM | `*` (wildcard) | 1 | 2 |

---

### 6. Artesão
| Property | Value |
|----------|-------|
| ID | `crafter` |
| Display Name | Artesão |
| Category | COMMON |
| Icon | `minecraft:crafting_table` |
| Description | Criação de itens via crafting |
| License Required | No |
| Unlocked By Default | Yes |
| Slot | COMMON_PRIMARY / COMMON_SECONDARY |
| Required Integration | None |
| Daily Limit | Unlimited |
| Max Level | 100 |

**How to earn money**: Craft items at a crafting table. Complex items like enchanting tables and beacons pay significantly more.

**How to earn XP**: Each crafted item grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| CRAFT-ITEM | `minecraft:chest` | 3 | 5 |
| CRAFT-ITEM | `minecraft:bookshelf` | 5 | 10 |
| CRAFT-ITEM | `minecraft:enchanting_table` | 15 | 30 |
| CRAFT-ITEM | `minecraft:beacon` | 200 | 500 |
| CRAFT-ITEM | `*` (wildcard) | 1 | 2 |

---

### 7. Explorador
| Property | Value |
|----------|-------|
| ID | `explorer` |
| Display Name | Explorador |
| Category | COMMON |
| Icon | `minecraft:compass` |
| Description | Exploração do mundo e descoberta de novos locais |
| License Required | No |
| Unlocked By Default | Yes |
| Slot | COMMON_PRIMARY / COMMON_SECONDARY |
| Required Integration | None |
| Daily Limit | Unlimited |
| Max Level | 100 |

**How to earn money**: Discover new biomes and structures. Rare biomes like Mushroom Fields and Deep Dark pay much more.

**How to earn XP**: Each discovery grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| EXPLORE | `minecraft:desert` | 10 | 20 |
| EXPLORE | `minecraft:jungle` | 15 | 25 |
| EXPLORE | `minecraft:mushroom_fields` | 25 | 50 |
| EXPLORE | `minecraft:deep_dark` | 50 | 100 |
| EXPLORE | `*` (wildcard) | 5 | 10 |

---

### 8. Guardião (Ranger)
| Property | Value |
|----------|-------|
| ID | `ranger` |
| Display Name | Guardião |
| Category | COMMON |
| Icon | `minecraft:bow` |
| Description | Combate contra criaturas hostis e proteção do mundo |
| License Required | No |
| Unlocked By Default | Yes |
| Slot | COMMON_PRIMARY / COMMON_SECONDARY |
| Required Integration | None |
| Daily Limit | Unlimited |
| Max Level | 100 |

**How to earn money**: Kill hostile mobs (zombie, skeleton, creeper, spider, enderman, witch, phantom, blaze, wither skeleton, ghast, guardian, elder guardian, evoker, ravager). Bosses and rare mobs pay more.

**How to earn XP**: Each kill grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| KILL-ENTITY | `minecraft:zombie` | 5 | 10 |
| KILL-ENTITY | `minecraft:creeper` | 8 | 15 |
| KILL-ENTITY | `minecraft:wither_skeleton` | 15 | 25 |
| KILL-ENTITY | `minecraft:elder_guardian` | 30 | 50 |
| KILL-ENTITY | `*` (wildcard) | 2 | 4 |

---

### 9. Culinarista
| Property | Value |
|----------|-------|
| ID | `culinarian` |
| Display Name | Culinarista |
| Category | COMMON |
| Icon | `minecraft:cake` |
| Description | Culinária e preparação de alimentos |
| License Required | No |
| Unlocked By Default | Yes |
| Slot | COMMON_PRIMARY / COMMON_SECONDARY |
| Required Integration | None |
| Daily Limit | Unlimited |
| Max Level | 100 |

**How to earn money**: Cook food via furnaces and crafting (bread, cooked meats, cake, pumpkin pie, golden apple, golden carrot). Elaborate foods pay more.

**How to earn XP**: Each prepared food item grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| CRAFT-ITEM | `minecraft:bread` | 2 | 4 |
| CRAFT-ITEM | `minecraft:cooked_beef` | 5 | 10 |
| CRAFT-ITEM | `minecraft:cake` | 12 | 25 |
| CRAFT-ITEM | `minecraft:golden_apple` | 20 | 50 |
| CRAFT-ITEM | `*` (wildcard) | 1 | 2 |

---

### 10. Mago
| Property | Value |
|----------|-------|
| ID | `magician` |
| Display Name | Mago |
| Category | COMMON |
| Icon | `minecraft:enchanted_book` |
| Description | Encantamento de itens e preparação de poções |
| License Required | No |
| Unlocked By Default | Yes |
| Slot | COMMON_PRIMARY / COMMON_SECONDARY |
| Required Integration | None |
| Daily Limit | Unlimited |
| Max Level | 100 |

**How to earn money**: Enchant items at an enchanting table. Brew potions at a brewing stand. Enchanting pays slightly more than brewing.

**How to earn XP**: Each enchantment or potion prepared grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| USE-MAGIC | `minecraft:enchanting_table` | 10 | 20 |
| USE-MAGIC | `minecraft:brewing_stand` | 8 | 15 |
| USE-MAGIC | `*` (wildcard) | 2 | 5 |

---

### 11. Pescador
| Property | Value |
|----------|-------|
| ID | `fisherman` |
| Display Name | Pescador |
| Category | COMMON |
| Icon | `minecraft:fishing_rod` |
| Description | Pesca em todos os biomas aquáticos |
| License Required | No |
| Unlocked By Default | Yes |
| Slot | COMMON_PRIMARY / COMMON_SECONDARY |
| Required Integration | None |
| Daily Limit | Unlimited |
| Max Level | 100 |

**How to earn money**: Fish with a fishing rod in any water body. Tropical fish pay more than common fish.

**How to earn XP**: Each fish caught grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| FISH | `minecraft:cod` | 3 | 6 |
| FISH | `minecraft:salmon` | 5 | 10 |
| FISH | `minecraft:tropical_fish` | 8 | 12 |
| FISH | `minecraft:pufferfish` | 6 | 10 |
| FISH | `*` (wildcard) | 2 | 4 |

---

## Pokemon Specializations (6)

### 12. Pesquisador Pokémon
| Property | Value |
|----------|-------|
| ID | `researcher` |
| Display Name | Pesquisador Pokémon |
| Category | POKEMON_SPECIALIZATION |
| Icon | `cobblemon:poke_ball` |
| Description | Captura de Pokémon e registro de novas espécies na Pokédex |
| License Required | **Yes** |
| License Objectives | Capture 50 Pokémon + Register 30 species in Pokédex |
| Unlocked By Default | No (needs `adept` rank milestone) |
| Slot | POKEMON_SPECIALIZATION |
| Required Integration | `cobblemon` |
| Daily Limit | $15,000 |
| Max Level | 100 |
| XP Curve | Polynomial (base=150, mult=1.2, exp=1.6) |
| Skill Points | Every 3 levels |

**How to earn money**: Capture wild Pokémon. Legendary Pokémon (e.g., Mewtwo, Rayquaza) pay $500 with 1000 XP. Registering new Dex entries grants bonus money and XP.

**How to earn XP**: Every capture and Dex entry grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| POKEMON-CAPTURED | `cobblemon:mewtwo` | 500 | 1000 |
| POKEMON-CAPTURED | `cobblemon:rayquaza` | 500 | 1000 |
| POKEMON-CAPTURED | `#rare_pokemon` | 50 | 80 |
| POKEMON-CAPTURED | `*` (wildcard) | 15 | 20 |
| DEX-ENTRY-ADDED | `*` | 25 | 50 |

**Related crates**: Specialist Crate, Researcher Crate

**Related contracts**: Daily: Catch X Pokemon. Weekly: Register X new Dex entries.

---

### 13. Criador Pokémon
| Property | Value |
|----------|-------|
| ID | `breeder` |
| Display Name | Criador Pokémon |
| Category | POKEMON_SPECIALIZATION |
| Icon | `cobblemon:rare_candy` |
| Description | Breeding e criação de ovos Pokémon |
| License Required | **Yes** |
| License Objectives | Hatch 10 eggs |
| Unlocked By Default | No (needs `adept` rank milestone) |
| Slot | POKEMON_SPECIALIZATION |
| Required Integration | `cobblemon` |
| Daily Limit | $15,000 |
| Max Level | 100 |
| XP Curve | Polynomial (base=150, mult=1.2, exp=1.6) |
| Skill Points | Every 3 levels |

**How to earn money**: Produce eggs via breeding and hatch them. Hatching pays 3x more than creating an egg.

**How to earn XP**: Each egg created and hatched grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| EGG-CREATED | `*` | 10 | 20 |
| EGG-HATCHED | `*` | 30 | 60 |

**Related crates**: Specialist Crate, Breeder Crate

**Related contracts**: Daily: Hatch X eggs. Weekly: Create X eggs.

---

### 14. Treinador Pokémon
| Property | Value |
|----------|-------|
| ID | `trainer` |
| Display Name | Treinador Pokémon |
| Category | POKEMON_SPECIALIZATION |
| Icon | `cobblemon:exp_share` |
| Description | Batalhas contra treinadores NPC |
| License Required | **Yes** |
| License Objectives | Win 25 battles against NPC trainers |
| Unlocked By Default | No (needs `adept` rank milestone) |
| Slot | POKEMON_SPECIALIZATION |
| Required Integration | `cobblemon` |
| Daily Limit | $15,000 |
| Max Level | 100 |
| XP Curve | Polynomial (base=150, mult=1.2, exp=1.6) |
| Skill Points | Every 3 levels |

**How to earn money**: Defeat NPC trainers in Pokemon battles. PvP battles are excluded. Trainer tiers mapped to reward tiers: GYM_LEADER, ELITE_FOUR, CHAMPION, TRAINER_COMMON.

**How to earn XP**: Each battle won grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| TRAINER-BATTLE-WON | `*` | 20 | 40 |

**Supports trainer tiers**: Higher-tier trainers get multiplier bonuses via `TrainerMappingService`.

**Cooldowns**: 24h for gym leaders/Elite Four/champion, 1h for common trainers.

**Related crates**: Specialist Crate, Trainer Crate

**Related contracts**: Daily: Win X battles. Weekly: Defeat X gym leaders.

---

### 15. Cuidador de Pasto
| Property | Value |
|----------|-------|
| ID | `pasture_keeper` |
| Display Name | Cuidador de Pasto |
| Category | POKEMON_SPECIALIZATION |
| Icon | `minecraft:hay_block` |
| Description | Gerenciamento de pastos Pokémon |
| License Required | **Yes** |
| License Objectives | Complete 25 pasture tasks |
| Unlocked By Default | No (needs `adept` rank milestone) |
| Slot | POKEMON_SPECIALIZATION |
| Required Integration | `cobblemon` (STUB — no event listener) |
| Daily Limit | $15,000 |
| Max Level | 100 |
| XP Curve | Polynomial (base=150, mult=1.2, exp=1.6) |
| Skill Points | Every 3 levels |

**Status**: BLOCKED_BY_ENVIRONMENT. Pasture mod is not detected in the modpack. Bridge enters `MOD_NOT_INSTALLED`.

**How to earn money**: Complete pasture tasks like feeding and collection. Currently only works via manual collection and contract delivery — no real-time event rewards.

**How to earn XP**: Each completed task grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| PASTURE-TASK-COMPLETED | `*` | 15 | 30 |

**Related crates**: Specialist Crate

**Related contracts**: Daily/Weekly: Complete X pasture tasks. Primary income source while pasture mod is unavailable.

---

### 16. Paleontólogo
| Property | Value |
|----------|-------|
| ID | `paleontologist` |
| Display Name | Paleontólogo |
| Category | POKEMON_SPECIALIZATION |
| Icon | `minecraft:bone` |
| Description | Revivescência de fósseis Pokémon pré-históricos |
| License Required | **Yes** |
| License Objectives | Revive 5 fossils |
| Unlocked By Default | No (needs `adept` rank milestone) |
| Slot | POKEMON_SPECIALIZATION |
| Required Integration | `cobblemon` (STUB — no event listener) |
| Daily Limit | $15,000 |
| Max Level | 100 |
| XP Curve | Polynomial (base=150, mult=1.2, exp=1.6) |
| Skill Points | Every 3 levels |

**Status**: BLOCKED_BY_ENVIRONMENT. Fossil mod is not detected. Bridge enters `MOD_NOT_INSTALLED`.

**How to earn money**: Revive fossils at compatible stations. Currently only works via contracts — no real-time event rewards.

**How to earn XP**: Each fossil revived grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| FOSSIL-REVIVED | `*` | 50 | 100 |

**Related crates**: Specialist Crate

**Related contracts**: Daily/Weekly: Revive X fossils. Primary income source.

---

### 17. Incursionista (Raider)
| Property | Value |
|----------|-------|
| ID | `raider` |
| Display Name | Incursionista |
| Category | POKEMON_SPECIALIZATION |
| Icon | `minecraft:totem_of_undying` |
| Description | Raids e batalhas em grupo contra chefes |
| License Required | **Yes** |
| License Objectives | Complete 5 raids |
| Unlocked By Default | No (needs `adept` rank milestone) |
| Slot | POKEMON_SPECIALIZATION |
| Required Integration | `cobblemon` (STUB — no event listener) |
| Daily Limit | $15,000 |
| Max Level | 100 |
| XP Curve | Polynomial (base=150, mult=1.2, exp=1.6) |
| Skill Points | Every 3 levels |

**Status**: BLOCKED_BY_ENVIRONMENT. Raid Dens mod is not detected. Bridge enters `MOD_NOT_INSTALLED`.

**How to earn money**: Complete Pokemon raids successfully. Currently only works via contracts — no real-time event rewards.

**How to earn XP**: Each raid completed grants XP.

**Actions**:

| Action Type | Target Examples | Money | XP |
|-------------|----------------|-------|-----|
| RAID-CLEARED | `*` | 40 | 80 |

**Related crates**: Specialist Crate

**Related contracts**: Daily/Weekly: Complete X raids. Primary income source.

---

## Summary

| # | ID | Display Name | Category | License | Slot | Integration |
|---|-----|-------------|----------|---------|------|-------------|
| 1 | miner | Minerador | COMMON | No | COMMON_PRIMARY | None |
| 2 | woodcutter | Lenhador | COMMON | No | COMMON_PRIMARY | None |
| 3 | farmer | Fazendeiro | COMMON | No | COMMON_PRIMARY | None |
| 4 | builder | Construtor | COMMON | No | Any COMMON | None |
| 5 | blacksmith | Ferreiro | COMMON | No | Any COMMON | None |
| 6 | crafter | Artesão | COMMON | No | Any COMMON | None |
| 7 | explorer | Explorador | COMMON | No | Any COMMON | None |
| 8 | ranger | Guardião | COMMON | No | Any COMMON | None |
| 9 | culinarian | Culinarista | COMMON | No | Any COMMON | None |
| 10 | magician | Mago | COMMON | No | Any COMMON | None |
| 11 | fisherman | Pescador | COMMON | No | Any COMMON | None |
| 12 | researcher | Pesquisador Pokémon | POKEMON | Yes | POKEMON_SPECIALIZATION | cobblemon |
| 13 | breeder | Criador Pokémon | POKEMON | Yes | POKEMON_SPECIALIZATION | cobblemon |
| 14 | trainer | Treinador Pokémon | POKEMON | Yes | POKEMON_SPECIALIZATION | cobblemon |
| 15 | pasture_keeper | Cuidador de Pasto | POKEMON | Yes | POKEMON_SPECIALIZATION | cobblemon* |
| 16 | paleontologist | Paleontólogo | POKEMON | Yes | POKEMON_SPECIALIZATION | cobblemon* |
| 17 | raider | Incursionista | POKEMON | Yes | POKEMON_SPECIALIZATION | cobblemon* |

\* = STUB integration (contracts only, no event listener)
