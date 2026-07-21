# Teste manual

1. Com Cobblemon 1.7.3 instalado, coloque um Pokémon na party e use `/pokemarket sell party 1 150000`.
2. Confirme que o Pokémon saiu da party, reinicie o servidor e use `/pokemarket browse`.
3. Com outro jogador, use `/pokemarket buy <id>` e depois `/pokemarket claim <id>`.
4. O vendedor deve retirar o claim de dinheiro usando o ID exibido pelo administrador/repository.
5. Repita usando `/pokemarket sell pc <box> <slot> 150000`.
6. Teste dois compradores simultâneos; apenas uma reserva deve vencer.
7. Teste cancelamento e expiração; ambos devem criar claim de Pokémon.
8. Encha party e PC antes do claim; o payload deve permanecer disponível.
9. Em ambiente de teste, injete uma falha em `AFTER_DEBIT`, reinicie o módulo e confirme que existe apenas um débito e que a compra termina com claims ou `RECONCILIATION_REQUIRED`.
10. Rode `./gradlew verifyCobblemonDependencies clean build`; confirme que os jars finais não contêm `com/cobblemon/mod/`.
