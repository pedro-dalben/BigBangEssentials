# Teste manual

## Fluxo visual

1. Abra `/pokemarket`, confirme que a Central mostra Explorar, Anunciar venda, Anunciar troca e Minha área.
2. Abra **Anunciar venda**, escolha Party ou PC e selecione um Pokémon.
3. Digite um preço inválido; confirme que a entrada é solicitada novamente.
4. Digite `cancel` e confirme que a tela de origem é reaberta sem criar anúncio.
5. Publique uma venda, confirme os dados exibidos e verifique que a lista de anúncios é atualizada.
6. Abra Explorar, alterne tipo, shiny, nível, IVs e ordenação; confirme que o estado aparece nos botões.
7. Defina preço mínimo/máximo, cancele a entrada e confirme que os filtros anteriores permanecem.
8. Com outro jogador, compre pela GUI; confirme que a tela muda para Claims após sucesso.
9. Abra Minha área e valide anúncios, compras, vendas, histórico, Claims e notificações.

## Fluxo transacional

10. Com Cobblemon 1.7.3 instalado, coloque um Pokémon na party e use `/pokemarket sell party 1 150000`.
11. Confirme que o Pokémon saiu da party, reinicie o servidor e use `/pokemarket browse`.
12. Com outro jogador, use `/pokemarket buy <id>` e depois `/pokemarket claim <id>`.
13. O vendedor deve retirar o claim de dinheiro usando o ID exibido pelo administrador/repository.
14. Repita usando `/pokemarket sell pc <box> <slot> 150000`.
15. Teste dois compradores simultâneos; apenas uma reserva deve vencer.
16. Teste cancelamento e expiração; ambos devem criar claim de Pokémon.
17. Encha party e PC antes do claim; o payload deve permanecer disponível.
18. Em ambiente de teste, injete uma falha em `AFTER_DEBIT`, reinicie o módulo e confirme que existe apenas um débito e que a compra termina com claims ou `RECONCILIATION_REQUIRED`.
19. Rode `./gradlew verifyCobblemonDependencies clean build`; confirme que os jars finais não contêm `com/cobblemon/mod/`.
