# PokéMarket — próximos passos

Este documento registra o estado atual da GUI e a ordem recomendada para as
próximas entregas. A ordem prioriza segurança transacional, clareza para o
jogador e prova em runtime antes de ampliar o escopo.

## Entregue nesta fase

- Cancelamento com confirmação, devolução automática para Party/PC e fallback para Claims.
- Atualização das listas de anúncios, registros, Claims, notificações, Party e PC após mutações.
- Central reduzida às ações principais.
- Submenu Minha área para anúncios, compras, vendas, histórico, trocas, Claims e notificações.
- Escolha única da origem da venda: Party ou PC.
- Retorno da entrada de preço ao cancelar, expirar ou informar preço inválido.
- Filtros com estado visível para espécie, tipo, shiny, nível, IVs, preço e ordenação.
- Confirmações com Pokémon, origem, validade e destino em Claims.

## Próxima ordem de implementação

### 1. Smoke visual e transacional real

Executar no servidor Fabric com Cobblemon 1.7.3 e banco configurado:

- abrir a Central e navegar por Minha área;
- publicar pela Party e pelo PC;
- cancelar preço, publicar, comprar e retirar Claims;
- cancelar anúncio com espaço e com Party/PC cheios;
- reiniciar o servidor entre escrow, compra e retirada;
- validar dois compradores concorrentes;
- registrar screenshots e resultado em `STAGING_UAT.md`.

Critério: nenhum fluxo de GUI é considerado concluído somente por compilação.

### 2. Trocas guiadas

- trocar o seletor de espécie baseado em anúncios ativos pelo catálogo de espécies válido do Cobblemon;
- mostrar resumo dos requisitos escolhidos antes de publicar;
- permitir requisitos de shiny, nível, IVs e forma com estado visual explícito;
- manter Party e PC como fontes de oferta;
- mostrar ao ofertante o requisito completo antes da confirmação;
- adicionar testes de espécie inexistente, forma incompatível e oferta inválida.

Não alterar a máquina transacional existente nem criar uma tabela nova para a GUI.

### 3. Claims e notificações enriquecidos

- exibir espécie, origem da devolução/compra/troca e valor de Claims;
- permitir abrir a ação relacionada a partir da notificação;
- manter IDs técnicos apenas no modo administrativo;
- testar idempotência, banco indisponível e storage cheio.

### 4. Painel Staff visual

Substituir comandos enviados ao chat por telas reais para:

- health rápido e completo;
- estatísticas;
- anúncios e operações pendentes;
- inspeção de listing;
- cancelamento/refund com motivo obrigatório;
- claims e operações que exigem reconciliação.

Toda ação monetária ou patrimonial deve continuar exigindo permissão, motivo,
auditoria e confirmação explícita.

### 5. API de consulta para a GUI

Remover SQL direto de `RecordsProvider` e expor consultas paginadas pelo
domínio PokéMarket. A GUI deve consumir services/repositories estáveis, sem
duplicar regras de status, ownership ou paginação.

### 6. Migração e manutenção

- manter menus customizados sem sobrescrever arquivos existentes;
- adicionar novos menus automaticamente apenas quando ausentes;
- documentar qualquer mudança de schema de menu;
- revisar handlers privados de comando que ficaram apenas por compatibilidade;
- adicionar telemetria mínima para falhas de abertura, refresh e Claims;
- repetir `common:test`, `:fabric:remapJar` e smoke runtime após cada fase.

## Fora do próximo corte

- entrada de preço por Anvil ou nova dependência;
- migração de banco para suportar a GUI;
- alteração da máquina de estados de compra/troca;
- mercado físico;
- redesign completo do Staff antes de validar as operações atuais.

## Definition of done

Uma fase só é encerrada quando tiver:

1. fluxo GUI implementado e parser dos menus aprovado;
2. teste automatizado proporcional ao risco;
3. `./gradlew :common:test --no-daemon` e `./gradlew :fabric:remapJar --no-daemon` aprovados;
4. cenário correspondente marcado no `STAGING_UAT.md`;
5. smoke real ou blocker de runtime registrado explicitamente.
