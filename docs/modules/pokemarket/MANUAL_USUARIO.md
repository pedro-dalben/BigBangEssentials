# PokéMarket — Manual do Usuário

O **PokéMarket** é o mercado virtual de Pokémon (Cobblemon) do BigBangEssentials. Ele permite que jogadores comprem, vendam e troquem Pokémon entre si de forma 100% segura, com menus visuais (GUI), sistema de garantia (*escrow*), filtros avançados e saldo em caixa (*claims*).

---

## 1. Como Acessar o PokéMarket

Para abrir a Central Visual do PokéMarket no jogo, use qualquer um dos seguintes comandos:

- `/pokemarket`
- `/gts`
- `/pm`

Ao executar o comando, o menu principal do PokéMarket será aberto na tela do seu jogador.

---

## 2. Menu Principal (Central)

No **Menu Principal** (`/pokemarket`), você encontrará as principais rotas de navegação:

| Ícone | Nome | Descrição |
|---|---|---|
| 🧭 **Bússola** | **Explorar anúncios** | Abre o catálogo completo de Pokémon à venda ou disponíveis para troca no servidor. |
| 🪙 **Barra de Ouro** | **Anunciar venda** | Inicia a publicação de um Pokémon por dinheiro (Coins/Economy). |
| 💎 **Esmeralda** | **Anunciar troca** | Inicia a publicação de um Pokémon em troca de outro com requisitos específicos. |
| 📦 **Baú** | **Minha área** | Central pessoal para ver anúncios ativos, histórico, notificações e resgatar **Claims**. |
| 📜 **Papel** | **Ajuda** | Envia uma mensagem explicativa rápida no chat. |
| 🧱 **Bloco de Comando** | **Painel Staff** | *(Exclusivo para Admins/Staff)* Menu administrativo de estatísticas e manutenção. |

---

## 3. Navegação e Filtros no Catálogo (Explorar)

Ao clicar em **Explorar anúncios**, você entra no catálogo interativo de ofertas.

### 🔍 Filtros Disponíveis
Na barra superior do menu de exploração, você encontra diversos botões para filtrar as ofertas:

1. 🧭 **Espécie:** Escolha uma espécie específica de Pokémon em uma lista em ordem alfabética (A–Z).
2. 💎 **Tipo:** Alterne entre exibir **Todos**, apenas **Vendas** (dinheiro) ou apenas **Trocas** (Pokémon).
3. ✨ **Shiny:** Alterne entre exibir **Todos**, apenas **Shiny** ou apenas **Não Shiny**.
4. 🧪 **Nível:** Filtre por faixas de nível.
5. 💎 **IVs:** Filtre por **Todos**, **3 IVs Perfeitos (31)** ou **6 IVs Perfeitos (31/31/31/31/31/31)**.
6. 💰 **Preço Mínimo / Máximo:** Clique para definir limites de preço por dinheiro.
   - *Entrada no Chat:* Ao clicar, o jogo solicitará que você digite o valor no chat. Digite o número ou envie `cancel` para desistir.
7. 📄 **Ordenação:** Alterne a exibição por **Recentes**, **Menor Preço**, **Maior Preço**, **Menor Nível** ou **Maior Nível**.
8. ❌ **Limpar filtros:** Reseta todos os filtros aplicados de volta ao padrão.

---

## 4. Como Comprar ou Trocar um Pokémon

### 💰 Comprando por Dinheiro
1. No catálogo, clique sobre o Pokémon desejado para abrir a tela de **Detalhes**.
2. Revise as informações: Nível, IVs perfeitos, Shiny, Vendedor, Valor e Tempo de Expiração.
3. Clique em **Comprar** (Ícone de Esmeralda).
4. Uma tela de **Confirmação de Compra** exibirá o valor final. Clique em **Confirmar Compra**.
5. O valor será debitado do seu saldo e o Pokémon comprado irá direto para seus **Claims** (Resgate).

### 🔄 Aceitando uma Troca
1. Ao abrir os Detalhes de um anúncio marcado como **Troca**, você verá os requisitos exigidos pelo vendedor (Espécie desejada, Shiny, Nível, IVs).
2. Clique em **Oferecer Pokémon da Party** ou **Oferecer Pokémon do PC**.
3. Selecione o Pokémon que você possui que atenda a todos os requisitos do vendedor.
4. Confirme a oferta na tela seguinte. Ambos os Pokémon serão transferidos com segurança via sistema de **Claims**.

---

## 5. Como Anunciar um Pokémon

### 🪙 Anunciar Venda por Dinheiro
1. No menu principal, clique em **Anunciar venda**.
2. Selecione a **Origem do Pokémon**:
   - **Party (Time):** Escolha um dos 6 slots do seu time.
   - **PC (Caixas):** Navegue pelas suas caixas do PC e selecione o Pokémon.
3. **Digitar o Preço:** Uma mensagem aparecerá no chat solicitando que você digite o preço de venda.
   - Digite um número válido (ex: `5000`).
   - Se mudar de ideia, digite `cancel` no chat para cancelar sem custos.
4. Na tela de **Confirmação de Anúncio**, confira o preço e a taxa de venda aplicável (ex: 5%).
5. Clique em **Publicar Anúncio**. O Pokémon sairá temporariamente da sua party/PC e ficará guardado com segurança em *Escrow* no PokéMarket.

### 🔄 Anunciar Troca por Pokémon
1. No menu principal, clique em **Anunciar troca**.
2. Selecione o Pokémon que deseja oferecer (Party ou PC).
3. No menu de **Requisitos da Troca**, defina o que você quer receber em troca:
   - **Espécie desejada:** Escolha a espécie que procura (ex: *Charizard*).
   - **Exigência Shiny:** Exigir Shiny, Proibir Shiny ou Qualquer Shiny.
   - **Nível / IVs Mínimos:** Defina restrições de nível e IVs.
4. Clique em **Publicar troca**. Seu Pokémon entrará em *Escrow* aguardando outro jogador aceitar a oferta.

---

## 6. Minha Área, Claims e Notificações

Na aba **Minha Área** (`/pokemarket` > **Minha Área** ou `/pokemarket claims`), você gerencia seus anúncios e saldo.

### 📥 Claims (Retiradas)
Sempre que você:
- **Comprar** um Pokémon;
- **Concluir uma troca**;
- Tiver um anúncio **expirado** ou **cancelado** por você;
- **Vender** um Pokémon por dinheiro;

Os Pokémon e/ou o dinheiro das suas vendas entram na sua conta de **Claims**.
- Clique no ícone do **Funil (Claims)**.
- Clique em um item específico ou no botão **Retirar tudo** para enviar os Pokémon de volta ao seu PC/Party e o dinheiro diretamente para a sua conta de economia.

### 📋 Meus Anúncios e Histórico
- **Meus Anúncios:** Lista todos os seus anúncios ativos no momento. Ao clicar em um anúncio seu, você pode **Cancelar o Anúncio** para receber seu Pokémon de volta via Claims.
- **Minhas Compras / Vendas / Trocas:** Histórico completo de todas as suas movimentações no mercado.

### 🔔 Notificações
- Notifica você ao entrar no servidor se houver vendas efetuadas ou itens para retirar.
- O menu de **Notificações** (Ícone de Sino) mostra alertas recentes e possui um botão **Marcar todas como lidas**.

---

## 7. Comandos CLI (Modo Texto)

Embora a interface gráfica (menus) seja o modo recomendado de uso, você também pode executar operações diretamente pelo chat se preferir:

| Comando | Descrição |
|---|---|
| `/pokemarket` (ou `/gts`, `/pm`) | Abre a central visual do mercado. |
| `/pokemarket browse [página]` | Abre o catálogo diretamente na página especificada. |
| `/pokemarket sell party <slot 1-6> <preço>` | Anuncia um Pokémon da Party por dinheiro. |
| `/pokemarket sell pc <caixa> <slot 1-30> <preço>` | Anuncia um Pokémon do PC por dinheiro. |
| `/pokemarket claim all` | Resgata todos os Pokémon e dinheiros pendentes em seus Claims. |
| `/pokemarket claims` | Abre o menu visual de Claims. |
| `/pokemarket history` | Abre o histórico pessoal de transações. |
| `/pokemarket notifications` | Abre a central de notificações. |
| `/pokemarket cancel <id>` | Cancela um anúncio ativo seu pelo ID. |

---

## 8. Guia Rápido para Staff / Administração

Membros da Staff com a permissão `bigbangessentials.pokemarket.admin` podem acessar o **Painel Staff** (`/pokemarket admin`):

- **Health Rápido / Completo:** Diagnóstico da integridade do banco de dados e do módulo.
- **Estatísticas:** Métricas do mercado (total de anúncios ativos, vendas efetuadas, movimentação financeira).
- **Inspeção de Anúncios e Operações:** Ferramentas para monitorar anúncios e verificar compras/trocas.
- **Cancelamento Administrativo:**
  - Comandos CLI: `/pokemarket admin cancel <id_anuncio> <motivo>`
  - Permite cancelar um anúncio irregular e devolver o Pokémon com segurança para os Claims do dono original.
