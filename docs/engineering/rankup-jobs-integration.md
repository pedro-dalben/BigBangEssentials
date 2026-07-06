# Integração RankUp - Jobs (Audit e Planejamento)

## Arquitetura Encontrada
Ambos `rankup` e `jobs` são pacotes dentro do módulo principal `BigBangEssentials` (ecosistema BigBangCraft).
- O **RankUp** define `RankupRank` que já possui `id`, `order` e `displayName` estáveis.
- O **RankUp** possui um `RankupManager` que gerencia a progressão e um `RankupPromotionService` para compras de rank.
- Alterações administrativas (ex: `/rankupadmin set`) ignoram o `RankupPromotionService` e alteram permissões no LuckPerms diretamente, não emitindo um evento padronizado de transição que o sistema de Jobs possa escutar.
- O **Jobs** implementa `JobRankProgressionProvider` em `JobRankMilestoneService`, que hoje está fortemente acoplado, acessando a instância Singleton de `RankupManager` e lendo a classe `RankupRank` diretamente.

## Pontos de Integração Existentes
- O `JobRankMilestoneService` lê `RankupManager.getInstance().getCurrentRank(playerId)` no login (`loadPlayer`).
- Ele itera sobre os milestones configurados no `JobsConfig` e destrava se `currentOrder >= def.requiredRankOrder()`.
- Falta escutar mudanças ativas de Rank durante o jogo de forma coesa (seja normal ou administrativa).

## Lacunas Encontradas
1. Faltam APIs bem definidas: Jobs depende de classes internas de RankUp (`RankupManager`, `RankupRank`).
2. Falta um ponto único/central para mudança de Rank (`RankTransitionService`), já que comandos admin e o rankup normal seguem fluxos separados.
3. Não existe um evento padronizado (ex: `RankTransitionCompletedEvent`) idempotente e comum a todos os fluxos de rankup (admin, player, etc).
4. Faltam comandos de sincronização explícita (`sync-rank`).
5. Falta registro claro/auditoria unificada de Rank transitions.

## Módulos Realmente Alterados
- `com.pedrodalben.bigbangessentials.api.rankup` (novo/extensão - Contratos públicos)
- `com.pedrodalben.bigbangessentials.rankup` (Implementação de APIs, Fluxo Central, Evento)
- `com.pedrodalben.bigbangessentials.jobs` (Adapter de consumo da API, Event Listener, Comandos de sincronização)

## Estratégia de Compatibilidade
- A classe atual `RankupRank` continuará sendo usada internamente, mas será mapeada para um `RankDefinition` simples para a API pública.
- A persistência atual do RankUp (transações) será estendida para garantir transições auditáveis para qualquer tipo de mudança de rank (não só progressão comprada, mas também admin set).
- Os IDs de Rank existentes no servidor continuam os mesmos.

## Riscos de Migração
- Risco de comandos de administração (ex: LuckPerms direto) burlarem o fluxo central. A equipe de staff precisará usar os comandos do `/rankupadmin` que passarão pelo fluxo central.
- Possíveis referências a nomes de Ranks em configs antigas precisam ser mantidas ou avisadas durante o boot.

## Ordem Segura de Implementação
1. **API Pública**: Criar `RankProgressionApi`, `RankDefinition`, e `RankTransitionCompletedEvent`.
2. **Fluxo Central RankUp**: Criar `RankTransitionService` agrupando qualquer mudança (normal ou admin).
3. **Refatoração RankUp**: Atualizar `RankupPromotionService` e `RankupAdminCommand` para delegar para o novo fluxo central.
4. **Adapter Jobs**: Fazer `JobRankMilestoneService` depender de `RankProgressionApi` em vez de `RankupManager` diretamente, e inscrever-se no evento `RankTransitionCompletedEvent`.
5. **Sync & Commands**: Adicionar comandos para sincronização em massa e verificação idempotente.
6. **Testes e Documentação**: Escrever testes e consolidar a documentação final.
