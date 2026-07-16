# BigBang Essentials - Tablist V2

## Arquitetura

1. **Cache e Compilação Antecipada**: `TabTemplateCompiler` compila templates em blocos estáticos/dinâmicos (`CompiledTabTemplate`). Textos não são reprocessados a cada tick.

2. **Dirty Flags**: Eventos (nick, permissão, tag, AFK, vanish) marcam `TabPlayerState` como "sujo". O coordinator processa apenas flags alteradas.

3. **Estado por Observador**: `ViewerTargetState` rastreia o último estado enviado para cada par viewer-target. Packets duplicados não são reenviados.

4. **Camada de Pacotes**: `NeoForgeTabPacketAdapter` abstrai APIs NeoForge. `ScoreboardTeamAdapter` gerencia teams por viewer.

5. **Fila de Packets**: Atualizações que excedem `maxPacketUpdatesPerTick` são enfileiradas e processadas no próximo tick.

## Ciclo de Vida

```
ServerStarted -> TablistModule.onEnable()
ServerTick.Post -> TablistModule.onServerTick()
PlayerLoggedIn -> TablistModule.onPlayerJoin()
PlayerLoggedOut -> TablistModule.onPlayerQuit()
ServerStopping -> TablistModule.onDisable()
/tablist reload -> TablistModule.onEnable()
```

Eventos conectados via `NeoForgeEvents` -> `TablistEventHandler` -> `TablistModule`.

## Variáveis (Placeholders Internos)

- `{prefix}` - Prefixo do grupo
- `{suffix}` - Sufixo do grupo
- `{tag}` - Tag selecionada
- `{name}` - Nome real ou nick (conforme `nameSource`)
- `{afk}` - Indicador AFK (config `afk.format`)
- `{ping}` - Latência em ms
- `{animation:<id>}` - Frame atual da animação
- Qualquer placeholder do `PlaceholderManager`: `{player_name}`, `{online}`, `{max}`

## Configuração `tablist.json` (V2)

### Estrutura

```json
{
  "_configVersion": 2,
  "tablist": {
    "enabled": true,
    "performance": {
      "fallbackRefreshTicks": 100,
      "maxPacketUpdatesPerTick": 250,
      "permissionRefreshTicks": 20
    },
    "headerFooter": {
      "enabled": true,
      "designs": [
        {
          "id": "default",
          "priority": 0,
          "default": true,
          "header": ["&6&lMeu Servidor", "{animation:header_anim}"],
          "footer": ["&7{online}&8/&7{max} online"]
        }
      ]
    },
    "playerList": {
      "enabled": true,
      "defaultFormat": "{prefix}{tag}{name}{suffix}{afk}",
      "nameSource": "NICK_OR_REAL",
      "groups": {
        "admin": { "format": "&c{prefix}{tag}{name}{suffix}{afk}" }
      }
    },
    "nameTags": {
      "enabled": true,
      "prefixFormat": "{prefix}{tag}",
      "suffixFormat": "{afk}",
      "collision": "ALWAYS",
      "nameVisibility": "ALWAYS"
    },
    "sorting": {
      "enabled": true,
      "rules": ["GROUP_PRIORITY:owner,admin,moderator,default", "AFK_LAST", "NAME_ASC"]
    },
    "visibility": {
      "hideVanished": true,
      "vanishBypassPermission": "bigbangessentials.vanish.see",
      "worldMode": "ALL"
    },
    "afk": {
      "enabled": true,
      "format": " &7[AFK]",
      "sortLast": true
    },
    "animations": {
      "header_anim": {
        "intervalTicks": 20,
        "mode": "LOOP",
        "frames": ["&aFrame 1", "&bFrame 2"]
      }
    }
  }
}
```

## Comandos

| Comando | Descrição | Permissão |
|---------|-----------|-----------|
| `/tablist` | Ajuda | OP |
| `/tablist reload` | Recarrega config | OP |
| `/tablist enable` | Ativa módulo | OP |
| `/tablist disable` | Desativa módulo | OP |
| `/tablist info` | Status do módulo | OP |
| `/tablist debug` | Diagnóstico | OP |
| `/tablist refresh all` | Força refresh total | OP |

## Integrações

| Sistema | Arquivo | Conectado |
|---------|---------|-----------|
| AFK | `AfkTabIntegration` | Sim (`AfkManager`) |
| Nick | `NickTabIntegration` | Sim (`NickCommand`) |
| Vanish | `VanishTabIntegration` | Sim (`VanishManager`) |
| Tag | `TagTabIntegration` | Sim (`TagCommands`, `TagManager`) |
| Permissão | `PermissionTabIntegration` | Sim (`PermissionsCommand`) |

## Limitações Conhecidas

1. **Display Name**: Usa reflection temporária em `ServerPlayer.tabListDisplayName` no NeoForgeTabPacketAdapter. O campo é restaurado após o envio do packet.
2. **UPDATE_LIST_ORDER**: Não implementado (requer Minecraft 1.21.2+).
3. **Objective no player list**: Não implementado (ping usa UPDATE_LATENCY).
4. **Below name objective**: Não implementado.
5. **Migração V1->V2**: Suportada, mas configurações V1 complexas podem precisar de ajustes manuais.

## Checklist para Teste em Servidor

1. **Login**: Entrar no servidor, verificar header/footer aparecem
2. **Header/Footer**: Usar `/tablist debug`, verificar tempo de tick
3. **Nick**: `/nick NovoNome`, verificar tablist atualizada
4. **Tag**: `/tag select <tag>`, verificar prefixo na lista
5. **AFK**: Aguardar AFK (ou `/afk` se implementado), verificar `[AFK]` na lista e ordenação
6. **Vanish**: `/vanish`, verificar que desaparece da tablist de jogadores sem permissão
7. **Unvanish**: `/vanish` novamente, verificar que reaparece
8. **Prefix/Grupo**: Alterar grupo via comando de permissions, verificar atualização
9. **Nametag**: Verificar prefixo/acima da cabeça para diferentes grupos
10. **Sorting**: Verificar ordem: grupos por prioridade, AFK no final, ordem alfabética
11. **Reload**: `/tablist reload` com jogadores online, verificar sem duplicação
12. **Logout/Relogin**: Sair e entrar, verificar estado persistido
13. **Múltiplos jogadores**: 3+ jogadores online, alterar nick de um, verificar que todos veem
