# BigBangEssentials - Sistema Interno de Menus (NeoForge)

Este documento descreve o funcionamento e a especificação de configuração do sistema interno de menus do BigBangEssentials.

---

## 1. Localização dos Arquivos de Configuração

Todos os menus são definidos em arquivos YAML (.yml ou .yaml) e devem ser armazenados no diretório:
`config/bigbangessentials/menus/` (ou `run/config/bigbangessentials/menus/` em ambiente de desenvolvimento).

O sistema carrega todos os arquivos deste diretório recursivamente durante a inicialização do servidor.

---

## 2. Comandos do Sistema

Administradores com a permissão `bigbangessentials.menu.admin` ou nível de OP 2+ podem utilizar o comando `/bbmenu`:

* **`/bbmenu list`**: Lista todos os menus válidos carregados e os inválidos que falharam na validação.
* **`/bbmenu validate`**: Realiza uma auditoria estática detalhada de todos os arquivos de menu carregados e exibe avisos e erros estruturados com a causa exata (ex.: slot inválido, ação desconhecida, etc.).
* **`/bbmenu reload`**: Recarrega todos os menus em tempo de execução sem necessitar reiniciar o servidor. Apenas arquivos válidos são ativados; arquivos com erros graves são temporariamente desabilitados.
* **`/bbmenu open <menuId>`**: Abre o menu especificado pelo ID para o jogador que executou o comando.

---

## 3. Estrutura Básica de um Menu

Um menu possui propriedades de raiz (Root) e uma ou mais páginas contendo itens.

```yaml
id: menu_id                 # ID do menu usado para abri-lo (ex: /bbmenu open menu_id)
schema-version: 1           # Versão do esquema de configuração (deve ser >= 1)
size: 54                    # Tamanho do inventário: múltiplo de 9 (entre 9 e 54)
title: "Título do Menu"     # Título do menu com suporte a cores e placeholders

flags:
  cache-rendered-items: false   # Otimização de caching de renderização
  close-on-world-change: true   # Fecha o menu se o jogador mudar de mundo
  prevent-item-take: true       # Bloqueia a remoção física de itens do menu

pages:
  main:                         # ID da página
    default-page: true          # Define esta como a página inicial padrão
    items:                      # Mapa de itens desta página
      # Definição dos itens...
```

---

## 4. Criação de Itens

Os itens são configurados sob o bloco `items` de cada página.

```yaml
items:
  item_id:
    slot: 13                    # Slot do item (0 a 53 para tamanho 54). Use 'slots: [10, 11]' para múltiplos slots.
    item:
      material-id: "minecraft:diamond"  # ID do material vanilla válido
      amount: 1                         # Quantidade (1 a 99)
      display-name: "&bItem de Teste"   # Nome do item formatado
      lore:                             # Descrição/Lore do item
        - "&7Esta é a lore do item."
        - "&eVocê está no mundo: &f{player_world}"
```

### Propriedades Adicionais de Itens:
* `close-on-click: true`: Fecha o menu ao clicar neste item.
* `refresh-on-click: true`: Atualiza a página atual ao clicar neste item.

---

## 5. Ações (Actions) e Deny-Actions

Você pode anexar gatilhos de ação ao clicar em um item usando `actions`. Se condições ou permissões forem violadas, o fluxo executa o bloco `deny-actions`.

```yaml
actions:
  - type: "send_message"
    params:
      message: "&aVocê clicou!"
deny-actions:
  - type: "send_message"
    params:
      message: "&cVocê não atende aos requisitos!"
```

### Ações Built-in Disponíveis:

* **`open_menu`**: Abre outro menu.
  * Parâmetros: `menu` (ID do menu alvo)
* **`close_menu`**: Fecha o menu do jogador.
* **`back_menu`**: Retorna para o menu anterior no histórico de navegação (Backstack).
* **`go_to_page`**: Navega para outra página do mesmo menu.
  * Parâmetros: `page` (ID da página alvo)
* **`send_message`**: Envia uma mensagem formatada ao jogador.
  * Parâmetros: `message` (Texto)
* **`run_player_command`**: Executa um comando do Minecraft com privilégios do jogador.
  * Parâmetros: `command` (Linha de comando sem a barra inicial `/`)
* **`run_console_command`**: Executa um comando do Minecraft pelo console do servidor.
  * Parâmetros: `command` (Linha de comando)
* **`refresh_menu`**: Atualiza completamente o menu atual.
* **`refresh_page`**: Atualiza a página atual.
* **`refresh_item`**: Atualiza o item clicado.
  * Parâmetros: `item_id` (Opcional)
* **`set_context_value`**: Armazena um valor de contexto mutável na sessão.
  * Parâmetros: `key` (Nome da chave), `value` (Valor a armazenar)
* **`remove_context_value`**: Remove um valor armazenado da sessão.
  * Parâmetros: `key` (Nome da chave)

---

## 6. Condições (Conditions)

As condições decidem se uma ação pode ser executada (quando associada a cliques) ou se um item é renderizado (quando associada à renderização).

```yaml
click-conditions:
  - type: "permission"
    params:
      permission: "bigbangessentials.vip"
    negate: false                   # Inverte o resultado da condição
    failure-message-key: "&cErro!"  # Mensagem caso a condição falhe
```

### Condições Built-in Disponíveis:

* **`permission`**: Requer que o jogador possua uma permissão.
  * Parâmetros: `permission` (permissão a verificar)
* **`has_all_permissions`**: Requer que o jogador possua TODAS as permissões listadas.
  * Parâmetros: `permissions` (lista de permissões ou string separada por vírgula)
* **`has_any_permission`**: Requer que o jogador possua pelo menos UMA das permissões listadas.
  * Parâmetros: `permissions` (lista de permissões ou string separada por vírgula)
* **`lacks_permission`**: Requer que o jogador NÃO possua a permissão.
  * Parâmetros: `permission` (permissão a verificar)
* **`context_present`**: Requer que a chave de contexto informada esteja definida na sessão.
  * Parâmetros: `key` (chave)
* **`context_equals`**: Requer que o valor de contexto armazenado seja igual ao valor esperado.
  * Parâmetros: `key` (chave), `value` (valor esperado)
* **`context_not_equals`**: Requer que o valor de contexto seja diferente do valor esperado.
  * Parâmetros: `key` (chave), `value` (valor esperado)
* **`page_index_at_least`**: Requer que o índice numérico da página seja maior ou igual ao valor.
  * Parâmetros: `value` (inteiro)
* **`page_index_at_most`**: Requer que o índice numérico da página seja menor ou igual ao valor.
  * Parâmetros: `value` (inteiro)
* **`current_page_is`**: Requer que a página atual seja exatamente igual ao ID informado.
  * Parâmetros: `page` (ID da página)

---

## 7. Permissões de Clique e Visualização (Permissions)

Além de `click-conditions` e `render-conditions`, os itens suportam atalhos nativos para checagem rápida de permissão:

* **`view-permission`**: Controla se o item aparece ou não no menu.
* **`click-permission`**: Controla se o jogador pode acionar as ações do item.

Exemplo de formato estruturado:
```yaml
click-permission:
  all-of: [ "bigbangessentials.menu.use", "bigbangessentials.menu.test" ]
  any-of: [ "bigbangessentials.admin", "bigbangessentials.mod" ]
  none-of: [ "bigbangessentials.banned" ]
  denied-message-key: "&cVocê foi impedido de clicar neste botão!"
```

---

## 8. Uso de Placeholders (Fase 5)

Os placeholders são resolvidos em tempo real no título do menu, nome do item, descrição (lore), parâmetros de ações e mensagens de negação.

### Placeholders built-in de Jogador:
* **`{player_name}`**: Nome de exibição do jogador.
* **`{player_uuid}`**: UUID do jogador.
* **`{player_level}`**: Nível de experiência.
* **`{player_health}`**: Vida atual (ex: 20.0).
* **`{player_food}`**: Nível de fome (0 a 20).
* **`{player_world}`**: Nome do mundo atual do jogador.

### Placeholders built-in de Servidor:
* **`{server_online_players}`**: Contagem de jogadores online.
* **`{server_max_players}`**: Limite máximo de jogadores.
* **`{server_time}`**: Hora atual do servidor (formato HH:mm).

### Placeholders de Menu e Sessão:
* **`{menu_id}`**: ID do menu ativo.
* **`{menu_page}`**: ID da página ativa.
* **`{menu_page_index}`**: Índice numérico da página atual.
* **`{menu_total_pages}`**: Número total de páginas do menu.
* **`{context:<key>}`**: Obtém o valor mutável associado à chave `<key>` armazenado no contexto da sessão.

---

## 9. Exemplos Completos de YAML

### Menu Principal (`main_menu.yml`)
```yaml
id: main_menu
schema-version: 1
size: 54
title: "&6&lMenu Principal &r- &7{player_name}"

pages:
  main:
    default-page: true
    items:
      gray_border:
        slots: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 50, 51, 52, 53]
        item:
          material-id: "minecraft:gray_stained_glass_pane"
          display-name: " "
      close_button:
        slot: 49
        item:
          material-id: "minecraft:barrier"
          display-name: "&cFechar Menu"
          lore:
            - "&7Clique para fechar o menu."
        actions:
          - type: "close_menu"
      msg_button:
        slot: 20
        item:
          material-id: "minecraft:paper"
          display-name: "&aEnviar Mensagem: {player_name}"
          lore:
            - "&7Envia uma mensagem de teste."
            - "&7Mundo atual: &f{player_world}"
        actions:
          - type: "send_message"
            params:
              message: "&a[BigBangEssentials] Olá, {player_name}! A navegação funciona."
      player_cmd_button:
        slot: 21
        item:
          material-id: "minecraft:diamond_sword"
          display-name: "&bExecutar Comando Jogador"
          lore:
            - "&7Executa o comando &f/ping&7."
        actions:
          - type: "run_player_command"
            params:
              command: "ping"
      console_cmd_button:
        slot: 22
        item:
          material-id: "minecraft:command_block"
          display-name: "&cExecutar Comando Console"
          lore:
            - "&7Executa &f/say&7 pelo console."
        actions:
          - type: "run_console_command"
            params:
              command: "say O jogador {player_name} clicou no menu!"
      open_second_button:
        slot: 23
        item:
          material-id: "minecraft:ender_pearl"
          display-name: "&dIr para Segundo Menu"
          lore:
            - "&7Clique para abrir o segundo menu."
            - "&7ID do Menu: &f{menu_id}"
        actions:
          - type: "open_menu"
            params:
              menu: "second_menu"
      permission_button:
        slot: 24
        item:
          material-id: "minecraft:gold_ingot"
          display-name: "&eBotão com Permissão"
          lore:
            - "&7Requer &fbigbangessentials.menu.test&7."
        click-permission:
          all-of:
            - "bigbangessentials.menu.test"
          denied-message-key: "&cVocê não tem permissão para usar esse botão!"
        actions:
          - type: "send_message"
            params:
              message: "&aVocê tem a permissão necessária!"
      deny_action_button:
        slot: 25
        item:
          material-id: "minecraft:tnt"
          display-name: "&cBotão com Condição e DenyAction"
          lore:
            - "&7Clique para testar falha."
        click-conditions:
          - type: "permission"
            params:
              permission: "bigbangessentials.nonexistent"
        actions:
          - type: "send_message"
            params:
              message: "&aEste texto nunca deve aparecer (sucesso)."
        deny-actions:
          - type: "send_message"
            params:
              message: "&c[DenyAction] Condição falhou com sucesso!"
      set_context_button:
        slot: 30
        item:
          material-id: "minecraft:emerald"
          display-name: "&aDefinir Contexto: Ativado123"
          lore:
            - "&7Define a chave 'test_key' como 'Ativado123'."
        actions:
          - type: "set_context_value"
            params:
              key: "test_key"
              value: "Ativado123"
          - type: "send_message"
            params:
              message: "&a[Context] test_key definido como Ativado123!"
      remove_context_button:
        slot: 31
        item:
          material-id: "minecraft:redstone"
          display-name: "&cRemover Contexto"
          lore:
            - "&7Remove a chave 'test_key'."
        actions:
          - type: "remove_context_value"
            params:
              key: "test_key"
          - type: "send_message"
            params:
              message: "&c[Context] test_key removido!"
      context_condition_button:
        slot: 32
        item:
          material-id: "minecraft:repeater"
          display-name: "&dBotão com Condição de Contexto"
          lore:
            - "&7Requer que 'test_key' seja 'Ativado123'."
            - "&7Valor atual: &f{context:test_key}"
        click-conditions:
          - type: "context_equals"
            params:
              key: "test_key"
              value: "Ativado123"
            failure-message-key: "&cErro: A chave de contexto 'test_key' não é 'Ativado123'!"
        actions:
          - type: "send_message"
            params:
              message: "&a[Sucesso] Condição de contexto atendida!"
        deny-actions:
          - type: "send_message"
            params:
              message: "&c[Falha] Requer que você clique no botão de Emerald primeiro!"
      back_button:
        slot: 40
        item:
          material-id: "minecraft:arrow"
          display-name: "&cVoltar (Backstack)"
          lore:
            - "&7Executa a ação de voltar (back_menu)."
        actions:
          - type: "back_menu"
```

### Segundo Menu (`second_menu.yml`)
```yaml
id: second_menu
schema-version: 1
size: 27
title: "&6&lSegundo Menu &r- &dPag. {menu_page}"

pages:
  main:
    default-page: true
    items:
      back_button:
        slot: 10
        item:
          material-id: "minecraft:arrow"
          display-name: "&eVoltar para Menu Principal"
          lore:
            - "&7Usa a ação back_menu para"
            - "&7retornar no histórico."
        actions:
          - type: "back_menu"
      msg_confirm_button:
        slot: 13
        item:
          material-id: "minecraft:writable_book"
          display-name: "&aStatus de Navegação"
          lore:
            - "&7Valor de contexto recebido:"
            - "&f{context:test_key}"
            - "&7(Deve ser 'Ativado123' se setado)"
        actions:
          - type: "send_message"
            params:
              message: "&a[BigBangEssentials] A navegação entre menus e o transporte de contexto funcionam perfeitamente!"
      close_button:
        slot: 16
        item:
          material-id: "minecraft:barrier"
          display-name: "&cFechar Menu"
        actions:
          - type: "close_menu"
```

---

## 10. Como Testar no Servidor

1. Inicie o servidor NeoForge local.
2. Certifique-se de que os arquivos `main_menu.yml` e `second_menu.yml` estejam em `run/config/bigbangessentials/menus/`.
3. No jogo ou console, execute `/bbmenu list` para confirmar que ambos foram carregados com sucesso.
4. Caso queira fazer modificações, edite os arquivos YAML diretamente e execute `/bbmenu reload` para atualizar as definições em runtime.
5. Use `/bbmenu validate` para conferir qualquer inconsistência em tempo real.
6. Execute `/bbmenu open main_menu` para abrir o menu principal e testar as interações e navegação entre o primeiro e segundo menu.

---

## 11. Limitações Conhecidas

* **Tipos de Container**: Suporte apenas a containers do tipo baú (Chest) genéricos com tamanhos múltiplos de 9, variando de 9 a 54 slots. Outros tipos de interfaces (como fornalha, bigorna, etc.) não são suportados nativamente pelo renderer.
* **Bloqueio de Interações**: As ações de inventário do jogador são bloqueadas para os slots do menu. No entanto, interações complexas do lado do cliente com modificadores visuais preditivos podem temporariamente desincronizar o cursor até que o servidor force a atualização de dados (`sendAllDataToRemote`).
* **Dependência de Inicialização no Teste**: A validação precisa do `material-id` contra o registro vanilla do Minecraft requer que o ambiente esteja totalmente bootstrappado. Em testes unitários offline onde o FML não está ativo, a validação de existência do material é ignorada caso as tabelas de registro estejam vazias.
* **Sem Editor In-Game**: A edição de menus é puramente declarativa via arquivos YAML; não há suporte a edição ou reposicionamento visual de itens dentro do jogo.
