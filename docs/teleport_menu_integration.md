# BigBangEssentials - Integração de Menus de Teleporte

Este documento descreve a integração do framework interno de menus com os sistemas de Warps, Homes e Player Warps (PWarps) no BigBangEssentials.

---

## 1. Configuração Global

A configuração do sistema de menus de teleporte fica localizada no arquivo de configuração global do mod (`config/bigbangessentials/config.yml` ou similar) sob a seção `teleport-menus`:

```yaml
teleport-menus:
  enabled: true                          # Ativa ou desativa globalmente a integração de menus
  allow-player-preferences: true         # Permite que jogadores alterem suas próprias preferências
  fallback-to-chat-if-menu-fails: true  # Se o carregamento do menu falhar, exibe a lista tradicional no chat
  auto-refresh-open-menus: true          # Atualiza automaticamente os menus abertos ao sofrer mutações

  # Modo de exibição padrão para cada comando quando executado sem parâmetros extras
  command-display-mode:
    warps: MENU
    homes: MENU
    pwarps: MENU
    teleports: MENU

  # Mapeamento do ID do menu que deve ser aberto para cada tipo de teleporte
  menus:
    main: "teleports_main_menu"
    warps: "warps_menu"
    homes: "homes_menu"
    pwarps: "pwarps_menu"
```

### Modos de Exibição (`CommandDisplayMode`)

O comportamento dos comandos `/warps`, `/homes`, `/pwarps` e `/teleports` pode ser configurado em três modos:

* **`MENU`**: Abre o menu GUI do inventário correspondente ao comando executado.
* **`CHAT`**: Mantém o comportamento antigo, exibindo a lista e links clicáveis diretamente no chat.
* **`BOTH`**: Abre o menu GUI e simultaneamente envia as mensagens e resumos no chat do jogador.

---

## 2. Preferências Individuais dos Jogadores

Se a configuração global `allow-player-preferences` estiver definida como `true`, cada jogador poderá alterar suas preferências individuais sobre o uso dos menus em relação ao chat.

Os comandos disponíveis são:

* **`/menus on`**: Ativa globalmente os menus para o jogador (os comandos passarão a abrir menus).
* **`/menus off`**: Desativa globalmente os menus para o jogador (comandos voltarão a usar o chat).
* **`/menuconfig warps <menu/chat>`**: Define a exibição específica para o comando `/warps`.
* **`/menuconfig homes <menu/chat>`**: Define a exibição específica para o comando `/homes`.
* **`/menuconfig pwarps <menu/chat>`**: Define a exibição específica para o comando `/pwarps`.
* **`/menuconfig reset`**: Redefine todas as preferências de exibição do jogador para os padrões definidos pelo servidor.

As preferências dos jogadores são persistidas em banco de dados ou arquivos de dados locais e são mantidas mesmo após reinicializações do servidor.

---

## 3. Data Providers Dinâmicos

Os seguintes provedores de dados dinâmicos foram implementados para alimentar as paginações dos menus:

### 1. `warps.global`
Provedor de todos os warps globais criados no servidor.
* **Filtros**: Respeita permissões individuais por warp se `per-warp-permission` estiver ativado no mod.
* **Placeholders Disponíveis**:
  * `{warp_id}`: ID de identificação técnica do warp.
  * `{warp_name}`: Nome amigável do warp.
  * `{warp_world}`: Nome do mundo onde o warp está localizado.
  * `{warp_dimension}`: Identificador de dimensão do warp (ex: `minecraft:overworld`).
  * `{warp_x}`, `{warp_y}`, `{warp_z}`: Coordenadas da localização.
  * `{warp_icon}`: Ícone de exibição padrão do item (ex: `minecraft:ender_eye`).

### 2. `homes.player`
Provedor que lista apenas as homes do próprio jogador visualizando o menu.
* **Placeholders Disponíveis**:
  * `{home_name}`: Nome da home.
  * `{home_world}`: Nome do mundo.
  * `{home_dimension}`: Identificador da dimensão (ex: `minecraft:overworld`).
  * `{home_x}`, `{home_y}`, `{home_z}`: Coordenadas da localização.
  * `{home_icon}`: Ícone de exibição padrão do item (ex: `minecraft:red_bed`).

### 3. `pwarps.public`
Provedor que lista todos os Player Warps públicos criados por jogadores no servidor.
* **Ordenação**: do maior número de visitas para o menor, com desempate por nome.
* **Placeholders Disponíveis**:
  * `{pwarp_id}`: ID de identificação técnica do player warp.
  * `{pwarp_name}`: Nome amigável do pwarp.
  * `{pwarp_owner_name}`: Nome do criador/dono do pwarp.
  * `{pwarp_owner_uuid}`: UUID do criador/dono do pwarp.
  * `{pwarp_world}`: Nome do mundo.
  * `{pwarp_dimension}`: Identificador da dimensão.
  * `{pwarp_x}`, `{pwarp_y}`, `{pwarp_z}`: Coordenadas.
  * `{pwarp_icon}`: Ícone padrão.
  * `{pwarp_public}`: Valor booleano ("true"/"false").
  * `{pwarp_visits}`: Total de visitas registradas no pwarp.

### 4. `pwarps.own`
Provedor que lista apenas os Player Warps criados pelo próprio jogador visualizando o menu.
* **Ordenação**: do maior número de visitas para o menor, com desempate por nome.
* Fornece os mesmos placeholders que `pwarps.public`. Utilizado principalmente para menus de edição, gerenciamento ou deleção rápida.

---

## 4. Ações de Teleporte (`Actions`)

Três novas ações foram adicionadas para uso nos cliques de itens de menu:

### 1. `teleport_warp`
Teleporta o jogador para o warp global especificado.
* **Parâmetros**: `warp-name` ou `warp-id`.
* **Regras de Negócio**: Executa as mesmas checagens do comando `/warp`, incluindo validação de permissões, cooldown, delay de teleporte, integridade física (prevenção de fuga da prisão) e segurança de destino.

### 2. `teleport_home`
Teleporta o jogador para a home especificada.
* **Parâmetros**: `home-name`.
* **Regras de Negócio**: Executa as mesmas checagens do comando `/home`. Se o clique for do tipo **RIGHT (Botão Direito)**, abre o menu de confirmação de exclusão da home.

### 3. `teleport_pwarp`
Teleporta o jogador para o Player Warp especificado.
* **Parâmetros**: `pwarp-name` ou `pwarp-id` e opcionalmente `pwarp-owner-uuid`.
* **Regras de Negócio**: Respeita privacidade (público/privado) e permissões. Se o clique for do tipo **RIGHT (Botão Direito)** e o jogador for o dono do pwarp, abre o menu de confirmação de exclusão do pwarp.

---

## 5. Menus Padrão (`Default Menus`)

Os arquivos YAML padrão fornecidos no classpath do mod estão localizados em:
`src/main/resources/default-config/bigbangessentials/menus/`

Eles são copiados automaticamente para o diretório de runtime do servidor (`config/bigbangessentials/menus/`) se não estiverem presentes:

1. **`teleports_main_menu.yml`**: Menu centralizado que exibe opções para navegar entre os Warps Globais, as Homes do Jogador e os Player Warps Públicos.
2. **`warps_menu.yml`**: Menu paginado que lista todos os warps globais disponíveis.
3. **`homes_menu.yml`**: Menu paginado que exibe as homes do jogador atual.
4. **`pwarps_menu.yml`**: Menu paginado que exibe todos os player warps públicos criados na comunidade.

---

## 6. Refresh Automático de Sessão

Para garantir consistência visual em tempo real sem causar overhead na CPU ou piscadas irritantes nos menus de outros jogadores, o sistema utiliza eventos internos do barramento NeoForge para atualização seletiva:

* **Criar/Deletar Warp**: Dispara a atualização global de todas as sessões abertas que consomem a fonte `warps.global`.
* **Criar/Deletar Home**: Dispara a atualização de página **apenas** para a sessão aberta do jogador afetado, mantendo a privacidade e reduzindo o consumo de recursos.
* **Criar/Deletar/Modificar PWarp**: Dispara a atualização global de todas as sessões abertas que consomem a fonte `pwarps.public` ou a fonte `pwarps.own` do jogador dono.

---

## 7. Status de Validação e Testes

### Testado por Unidade (Unit Tested)
* **Data Providers**: `GlobalWarpsMenuDataProvider`, `PlayerHomesMenuDataProvider` e `PublicPlayerWarpsMenuDataProvider` validados com mocks no JUnit.
* **Menu Action Handlers**: `TeleportToWarpMenuAction` e `TeleportToHomeMenuAction` validados isoladamente através da arquitetura de `Runner` desacoplada (execução assíncrona/síncrona).
* **Player Preferences**: Validação de redefinição, salvamento e persistência das configurações de preferências de exibição de menus.
* **Pagination**: Lógica de paginação e controle de botões de navegação.

### Testado por Compilação (Compilation Tested)
* Todo o framework de integração de menus e comandos NeoForge compila com sucesso via `./gradlew compileJava` e `./gradlew classes`.
* Compatibilidade com JUnit 5 e Mockito 5.

### Testado em Servidor Real (Runtime Server Tested)
* **Instalação Limpa**: Extração automática e correta dos 6 arquivos YAML padrão do classpath para a pasta do runtime do servidor (`run/config/bigbangessentials/menus/`).
* **Carregamento de YAML**: Validação bem-sucedida de todos os YAMLs carregados (`homes_menu`, `warps_menu`, `pwarps_menu`, `confirm_delete_home`, etc.) na inicialização do servidor.
* **Classloading e Dependências**: Verificação de resolução e carregamento de classes de dependências de terceiros (SnakeYAML, Java-WebSocket) no ClassLoader do NeoForge/ModLauncher em runtime real.
* **Bypass de Validação de Placeholders**: O parser de YAML foi corrigido para ignorar a validação de existência estática de IDs de materiais que possuem expressões de placeholder dinâmicas (ex: `{home_icon}`, `{warp_icon}`), permitindo a inicialização limpa do mod.

### Não Testado (Not Tested)
* Interação de rede multijogador em escala extrema (mais de 100 conexões/segundo simultâneas no sistema de menus).
* Comportamento com clientes modificados/hackeados tentando forçar envio de pacotes de clique adulterados a nível de protocolo.

### Limitações Conhecidas (Known Limitations)
* **Delay de Teleporte e Movimento**: Se o delay de teleporte estiver ativado, a movimentação do jogador fechará o inventário atual e cancelará o processo por razões de segurança.
* **Interface Textura Vanilla**: O visual do menu padrão se baseia nas texturas de baú vanilla do Minecraft. Customizações completas de fonte ou texturas customizadas exigem o uso de Resource Packs auxiliares.
* **Ações de Exclusão**: A exclusão via menu confirma a revalidação imediata de posse e integridade do pwarp/home no momento do clique, abortando com mensagem de erro caso o contexto tenha sido modificado.
* **Cliques Especiais (SHIFT/NUMBER/DROP)**: Bloqueados por padrão nas ações, a menos que declarados explicitamente no campo `clicks` do YAML.

---

## 8. Como Testar em Servidor Real

1. Inicialize o servidor com o mod instalado.
2. Acesse a pasta do servidor em `config/bigbangessentials/menus/` e confirme se os arquivos padrões de configuração foram extraídos corretamente do mod.
3. Conecte com um cliente Minecraft.
4. Execute os comandos `/warps`, `/homes` e `/pwarps` para abrir as respectivas GUIs.
5. Adicione novas homes usando `/sethome <nome>` ou warps globais e confirme que a GUI se atualiza dinamicamente e sem fechar se estiver aberta.
6. Digite `/menuconfig warps chat` e execute `/warps` para certificar-se de que a listagem agora é impressa no chat. Execute `/menuconfig reset` para restaurar o comportamento da GUI.
