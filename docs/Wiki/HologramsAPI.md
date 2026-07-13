# BigBangHolograms — API

## Entry point

```java
import com.pedrodalben.bigbangessentials.holograms.api.BigBangHolograms;
import com.pedrodalben.bigbangessentials.holograms.api.HologramService;

HologramService api = BigBangHolograms.getApi();
```

## ID namespace

IDs seguem o formato `namespace:caminho`:

```text
bigbangessentials:admin/spawn
bigbangessentials:admin/regras
bigbangessentials:admin/loja
```

IDs simples são normalizados automaticamente:
- `spawn` → `bigbangessentials:admin/spawn`

## CRUD

```java
// Criar
HologramDefinition def = HologramDefinition.builder("bigbangessentials:admin/myhologram")
    .ownerId("bigbangessentials:admin")
    .location(new HologramLocation(dimension, x, y, z))
    .lines(List.of("&6Linha 1", "&fLinha 2"))
    .viewDistance(32)
    .persistent(true)
    .build();

HologramHandle handle = api.create(def);

// Buscar
Optional<HologramDefinition> found = api.findDefinition("bigbangessentials:admin/myhologram");

// Atualizar
api.update("bigbangessentials:admin/myhologram", builder -> builder
    .displayDistance(48)
    .updateDistance(24));

// Deletar
api.delete("bigbangessentials:admin/myhologram");

// Verificar existência
boolean exists = api.exists("bigbangessentials:admin/myhologram");
```

## HologramDefinition builder

```java
HologramDefinition.builder(id)
    .ownerId("bigbangessentials:admin")
    .location(location)
    .displayName("Spawn")
    .enabled(true)
    .lines(List.of("&6Linha 1"))
    .pages(List.of(page1, page2))
    .viewDistance(32)
    .displayDistance(48)
    .updateDistance(24)
    .visibilityPolicy(HologramVisibilityPolicy.NEARBY_PLAYERS)
    .updatePolicy(HologramUpdatePolicy.DYNAMIC)
    .persistenceMode(HologramPersistenceMode.PERSISTENT)
    .refreshIntervalTicks(40)
    .pageSwitchIntervalTicks(100)
    .offset(0, 0, 0)
    .lineWidth(240)
    .textOpacity((byte) -1)
    .backgroundColor(0)
    .shadow(true)
    .seeThrough(false)
    .billboard(Display.BillboardConstraints.CENTER)
    .scale(1.0F)
    .hideInSpectator(true)
    .requiredPermission("bigbangessentials.vip")
    .flags(EnumSet.of(HologramFlag.STATIC_CONTENT))
    .build();
```

## Páginas e linhas

```java
// Criar página
HologramPage page = new HologramPage(
    List.of(HologramLine.text("&6Linha 1"), HologramLine.text("&fLinha 2")),
    List.of(new HologramAction(HologramActionTrigger.RIGHT_CLICK, 
        HologramActionType.PLAYER_COMMAND, "/menu")),
    100,  // durationTicks
    "bigbangessentials.vip",  // requiredPermission
    EnumSet.noneOf(HologramFlag.class)
);

// Tipos de conteúdo
HologramLine textLine = HologramLine.text("&6Meu texto");
HologramLine itemLine = HologramLine.item("minecraft:diamond");
HologramLine headLine = HologramLine.head("Notch");
HologramLine compLine = HologramLine.component(myComponent);
```

## Placeholder resolvers

```java
api.registerPlaceholderResolver(new HologramPlaceholderResolver() {
    @Override public boolean supports(String placeholder) { return "myplugin_var".equals(placeholder); }
    @Override public boolean isPlayerScoped() { return false; }
    @Override public String resolve(String placeholder, HologramDefinition def, ServerPlayer viewer) {
        return "valor";
    }
});
```

## Eventos

```java
HologramEventBus.get().register(event -> {
    if (event instanceof HologramClickEvent click) {
        System.out.println(click.getPlayer() + " clicou em " + click.getDefinition().id());
    }
});
```

Eventos disponíveis:
- `HologramCreateEvent`
- `HologramDeleteEvent`
- `HologramEnableEvent` / `HologramDisableEvent`
- `HologramShowEvent` / `HologramHideEvent`
- `HologramClickEvent` (com trigger type e page index)
- `HologramMoveEvent`

## Lifecycle listeners

```java
api.registerLifecycleListener(new HologramLifecycleListener() {
    @Override public void onCreated(HologramDefinition definition) {}
    @Override public void onDeleted(String hologramId) {}
    @Override public void onShown(HologramDefinition definition, ServerPlayer viewer) {}
    @Override public void onHidden(HologramDefinition definition, ServerPlayer viewer) {}
    @Override public void onUpdated(HologramDefinition definition) {}
});
```
