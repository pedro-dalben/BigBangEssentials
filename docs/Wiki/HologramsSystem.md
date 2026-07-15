# BigBangHolograms — Sistema de Hologramas

BigBangHolograms é o sistema administrativo e público de hologramas do BigBangEssentials, inspirado funcionalmente no DecentHolograms.

## Características

- **Entidades virtuais**: hologramas existem apenas no cliente, sem entidades persistentes no mundo
- **Multi-página**: cada holograma pode ter múltiplas páginas com rotação automática
- **Conteúdo rico**: texto, itens, cabeças de jogador, animações
- **Placeholders**: {player}, {online}, {world}, {x}/{y}/{z} e extensível via API
- **Animações**: typewriter, scroll, rainbow, burn, wave
- **Ações**: comandos, mensagens, sons, teleporte ao clicar
- **Permissões granulares**: controle por comando e por holograma
- **Persistência segura**: arquivo por holograma, escrita atômica, schema versionado
- **Diagnóstico**: métricas de performance, cache hit rate, packet counters
- **Multi-loader**: Fabric e NeoForge com bridges de plataforma

## Comandos rápidos

```mcfunction
/bbholo create spawn "&6&lBIGBANGCRAFT"
/bbholo line add spawn "&fBem-vindo, {player}!"
/bbholo line add spawn "&eUse /menu para começar"
/bbholo displayrange spawn 32
/bbholo updaterange spawn 16
/bbholo movehere spawn
```

## Atalhos

- `/bbholo` (principal)
- `/hologram` (alias)
- `/holograms` (alias)
- `/holo` (alias)

## API

```java
// Criar holograma
HologramDefinition definition = HologramDefinition.builder("bigbangessentials:admin/spawn")
    .ownerId("bigbangessentials:admin")
    .location(location)
    .lines(List.of("&6&lBIGBANGCRAFT", "&fBem-vindo, {player}!"))
    .persistent(true)
    .build();

BigBangHolograms.getApi().createOrUpdate(definition);

// Buscar/Buscar/Excluir
Optional<HologramDefinition> def = BigBangHolograms.getApi().findDefinition("bigbangessentials:admin/spawn");
BigBangHolograms.getApi().delete("bigbangessentials:admin/spawn");
```

## Estrutura de arquivos

Cada holograma persistente é salvo em:
```
config/bigbangessentials/holograms/<id>.json
```

## Separação das Crates

O módulo de crates usa `bigbangessentials:crate` como owner e IDs `bigbangessentials:crate/<uuid>`.
Comandos administrativos normais não mostram hologramas de sistema por padrão.
Use `--all` ou `--owner bigbangessentials:crate` para visualizá-los.
