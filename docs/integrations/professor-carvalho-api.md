# API do Professor Carvalho

`BigBangEssentialsApiProvider.get()` entrega a única fachada pública destinada
ao gateway. Ela retorna DTOs imutáveis, não expõe repositórios nem permite
alteração de saldo durante a leitura. Moedas usam `BigDecimal`; gemas usam
inteiros. Rank e jobs são opcionais, e tempo jogado ainda é explicitamente
indisponível até existir uma fonte persistida confiável.

`getPlayerProfile(UUID)` é assíncrono. Para economia em banco ele usa o executor
de banco já pertencente ao BigBangEssentials; consumidores devem aplicar seus
próprios timeouts e tratar cada campo ausente como módulo indisponível. Recompensas
não fazem parte desta API nesta fase: no futuro deverão usar
`IdempotentEconomyService.credit` e uma chave de idempotência para gemas.
