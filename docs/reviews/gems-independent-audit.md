# Auditoria Independente do Sistema Gems (Gems Wallet System Audit)

Este documento apresenta a revisão independente profunda do sistema de Gems integrado no **BigBangEssentials**, realizada antes de sua integração com o **BigBang Regions**.

---

## 1. Informações de Baseline e Identificação

- **SHA Auditado:** `037bafb86fcf70357fb4069956dcad36d146b32c`
- **Branch:** `master`
- **Mudanças Locais:** Nenhuma (Diretório de trabalho limpo).
- ** Loader & Java:** Java 21 LTS, carregadores Fabric e NeoForge.
- **Dependências Novas:** Nenhuma. O sistema usa apenas dependências nativas e bibliotecas de utilidade internas do projeto (GSON).

### Commits Relacionados ao Sistema Gems
- `037bafb8` feat(gems): implement copy-on-write transaction model and crash failpoints (Auditoria e Durabilidade)
- `5bcc5bad` test: add comprehensive gems test suite and isolation
- `ac5bc86f` feat: add gems commands ledger and placeholders
- `2eefc405` feat: add configurable gems wallet persistence and recovery
- `0eca9a2d` docs: define gems wallet architecture and Regions integration contract

---

## 2. Inventário Técnico Real do Sistema Gems

Revisão detalhada de todas as classes que compõem o ecossistema Gems:

| Camada | Classe / Arquivo | Responsabilidade | Estado Mutável | Persistência Usada | Thread Usada | Risco Identificado |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **API** | `GemsService` | Contrato público estável para consumo de outros mods | Imutável | Nenhuma | Thread chamadora do cliente | Exposição acidental de managers internos (Resolvido via interface limpa) |
| **API** | `BigBangEssentialsApi` | Ponto de entrada de acesso seguro à API do mod | Imutável | Nenhuma | Thread chamadora | N/A |
| **Core** | `GemsManager` | Gerenciador de estado, concorrência e operações | `currentState` (GemsState) e `idempotencyRegistry` | Delegada à `GemsPersistence` | Sincronizado por `ReentrantReadWriteLock` | Concorrência e descompasso memória/disco (Resolvido via Copy-on-Write) |
| **Domain** | `GemReservation` | Representa uma reserva de saldo temporária | `status`, `expiresAt`, `capturedAt`, `releasedAt` | Nenhuma | Sincronizada no Manager | Mutação acidental pós-falha (Resolvido via deep copy) |
| **Persistence** | `GemsPersistence` | Leitura e gravação de arquivos JSON e Ledger | Nenhuma (Stateless) | `gems_state.json`, `gems_transactions.jsonl` | Sincronizado internamente | I/O síncrono lento (Mitigado com atomic write síncrono obrigatório) |
| **Persistence** | `GemsState` | Estrutura de dados interna do wallet state | Mapas de saldos e reservas | `gems_state.json` | Nenhuma | N/A |

### Isolamento de Dependências (Verificado)
Gems não importa nenhuma dependência do `BigBang Regions` (ex: `BigBangRegions`, `Region`, `RegionResizeService`, `PlotSlot`). O acoplamento é estritamente de consumo da API do Essentials pelo Regions. Gems também está 100% isolado da economia de `Coins`, não compartilhando `EconomyManager`, `balances.json` ou `transactions.json`, e não utilizando a integração com o Vault.

---

## 3. Estratégia de Persistência e Durabilidade

Optou-se pela **Estratégia A — State authoritative + ledger reconciliável**:
- **gems_state.json** é a fonte de verdade absoluta do saldo e das reservas ativas.
- **gems_transactions.jsonl** é um log de auditoria cronológico (Audit Log), e não um Write-Ahead-Log (WAL) autoritativo.
- **Incremento de Revisão:** Cada gravação de estado incrementa atomicamente o campo `revision` no arquivo e gera um identificador único de transação (`transactionId`).

### Ordem Real de Persistência (Copy-on-Write)
Para eliminar qualquer risco de divergência entre a memória (cache) e o disco no caso de uma falha de escrita, foi implementado o padrão **Copy-on-Write (CoW)**:
1. O lock de escrita é adquirido (`stateLock.writeLock().lock()`).
2. O estado atual é clonado profundamente (`currentState.cloneState()`), incluindo a clonagem profunda de objetos mutáveis (`GemReservation.copy()`).
3. As alterações são aplicadas estritamente no estado clonado (`nextState`).
4. O clone é persistido síncrona e atomicamente em disco:
   - Gravação física no arquivo temporário `gems_state.json.tmp`.
   - Movimentação atômica (`Files.move`) com `ATOMIC_MOVE` e `REPLACE_EXISTING` (caindo de volta para substituição via cópia apenas em caso de limitação do sistema de arquivos).
5. Se e somente se a escrita em disco for bem-sucedida:
   - O failpoint `BEFORE_CACHE_SWAP` é validado.
   - A referência interna em memória é atualizada: `currentState = nextState`.
   - O evento da transação é anexado ao log de auditoria (`appendTransaction`).
   - O registro é inserido no mapa de idempotência em cache.
   - O evento do ciclo de vida é disparado de forma segura (`postEventSafely`).
6. Caso a gravação em disco falhe (ex: falta de espaço em disco), o estado em memória permanece intocado e o erro é retornado de forma limpa, garantindo integridade transacional absoluta.

---

## 4. Testes de Injeção de Falhas (Crash Injection)

Através do mecanismo `GemsPersistenceFailpoint`, foram simuladas interrupções e falhas de processo em diversas etapas das mutações. Os resultados observados nos testes automatizados mostram a resiliência do sistema:

- **BEFORE_WRITE_TEMP / AFTER_WRITE_TEMP:**
  - *Comportamento:* A escrita do novo estado falha ou é interrompida.
  - *Integridade:* A alteração em memória é descartada devido ao Copy-on-Write. O arquivo em disco permanece intacto. Saldo e heldBalance permanecem corretos na memória e após reinicializações.
- **BEFORE_ATOMIC_MOVE:**
  - *Comportamento:* O arquivo temporário `.tmp` é escrito com sucesso, mas o rename atômico falha.
  - *Integridade:* O estado permanente não sofre alteração. O arquivo temporário é ignorado no boot. O saldo permanece intacto.
- **BEFORE_CACHE_SWAP:**
  - *Comportamento:* O estado permanente é gravado com sucesso no disco, mas o processo aborta antes de atualizar a memória em tempo de execução.
  - *Integridade:* Durante a execução atual, o saldo em cache permanece o antigo. No entanto, no reinício (boot), o recovery lê o novo estado persistido com sucesso, restaurando a consistência exata do saldo e held balance sem perdas.
- **BEFORE_APPEND_LEDGER:**
  - *Comportamento:* O estado é salvo e a memória é atualizada com sucesso, mas o log de auditoria falha ao registrar o evento.
  - *Integridade:* O saldo e a reserva são válidos. A inconsistência é puramente de log, que é detectada no boot por meio da reconciliação das reservas ativas em relação ao ledger de auditoria.

---

## 5. Concorrência e Invariantes

Nosso sistema de concorrência foi validado por meio do JUnit sob o agendador `@Isolated`. Cenários testados com threads simultâneas em barreira cíclica (`CountDownLatch`):

1. **Reservas Concorrentes (Saldo Insuficiente):** Em concorrência múltipla acima do limite de saldo, apenas o número exato de reservas permitidas pelo saldo disponível é concedido. Nenhuma sobressaturação de saldo (overdraft) ocorre.
2. **Captura e Liberação Simultâneas:** As transações são exclusivas. Se a captura ganha a corrida, o release subsequente falha com `RESERVATION_ALREADY_CAPTURED`. Se a liberação ganha a corrida, a captura falha com `RESERVATION_ALREADY_RELEASED`.
3. **Múltiplos Retries de Capturas (Idempotência):** Retentativas concorrentes e sequenciais para capturar a mesma reserva retornam sucesso (`success=true`), mas debitam o total da carteira exatamente uma vez.
4. **Múltiplos Retries de Liberações (Idempotência):** Retentativas concorrentes e sequenciais para liberar a mesma reserva retornam sucesso (`success=true`), mas liberam o saldo hold exatamente uma vez.

### Invariantes Verificadas pós-Operações
- `totalBalance >= 0` (Não há saldo negativo).
- `heldBalance >= 0` e `heldBalance <= totalBalance`.
- `totalBalance = availableBalance + heldBalance`.
- Nenhuma reserva ativa duplicada ou ressuscitada.

---

## 6. Acordo de Integração (Regions Gems Contract)

A integração futura com o **BigBang Regions** está estruturada sob o seguinte ciclo de vida:

```
1. Regions gera um UUID (operationId) e salva em seu armazenamento local.
2. Regions calcula o custo da operação e monta a idempotencyKey:
   "bigbangregions:resize:<regionId>:<operationId>"
3. Regions chama reserve(...) na Gems API.
4. O Essentials retorna o reservationId da reserva ativa.
5. Regions grava o estado PAYMENT_RESERVED no seu banco/arquivo local.
6. Regions realiza o redimensionamento técnico do terreno no mundo Minecraft.
7. Regions grava o estado RESIZE_APPLIED no seu banco local.
8. Regions chama capture(reservationId) para debitar permanentemente as Gems.
9. Essentials processa o débito único.
10. Regions grava PAYMENT_CAPTURED / COMPLETED.
```

### Contrato de Leases (Prazos e Renovação)
- **Lease Padrão (defaultLeaseSeconds):** 900 segundos (15 minutos).
- **Lease Máxima (maxLeaseSeconds):** 3600 segundos (1 hora).
- **Renovação:** Permitida e totalmente recomendada para operações de redimensionamento longas. A renovação (`renew()`) deve ser chamada pelo Regions antes do término do tempo limite da reserva se o redimensionamento ainda estiver pendente.
- **Expiração:** Se o tempo limite da reserva expirar antes do Regions enviar a chamada de `capture()`, os fundos são automaticamente devolvidos ao saldo disponível do jogador. A tentativa subsequente de capture falhará com `RESERVATION_EXPIRED`. O Regions deve reverter a operação localmente se detectar expiração de lease.

---

## 7. Achados e Correções Efetuadas

Durante a auditoria profunda, os seguintes pontos foram identificados e corrigidos:

1. **Achado #1 (CRITICAL): Divergência de Cache/Disco sob Falha de I/O**
   - *Problema:* Os métodos de alteração de saldo mutavam o mapa `currentState.balances` e o status das reservas antes de invocar `saveState()`. Se a persistência em disco falhasse, o estado em memória continuava com o valor alterado, divergindo do arquivo físico.
   - *Solução:* Implementado o padrão Copy-on-Write (CoW). A mutação só é aplicada na referência principal `currentState` após a gravação síncrona com sucesso no disco.
2. **Achado #2 (HIGH): Possibilidade de Liberação Manual Sem Confirmação**
   - *Problema:* O comando `/gems admin reservation release <id>` permitia liberação de fundos imediatos com apenas um clique/execução, sem confirmação literal.
   - *Solução:* Brigadier reforçado para exigir obrigatoriamente o literal `"confirm"` na sintaxe do comando, prevenindo execuções acidentais de operadores.
3. **Achado #3 (MEDIUM): Idempotência com Payload Divergente**
   - *Problema:* Retentativas de transações com a mesma chave de idempotência e dados divergentes podiam expor inconsistência de dados.
   - *Solução:* Implementada verificação detalhada que compara o player, valor e tipo na requisição idempotente e retorna explicitamente `IDEMPOTENCY_CONFLICT` se os dados divergirem do payload original gravado.

---

## 8. Veredito Final

Com base na auditoria completa de durabilidade, testes manuais e automatizados, e conformidade com os requisitos de isolamento e idempotência:

```txt
GEMS_API_APPROVED_FOR_REGIONS_INTEGRATION
```

O sistema Essentials Gems está 100% pronto e seguro para consumo da API do Regions.
