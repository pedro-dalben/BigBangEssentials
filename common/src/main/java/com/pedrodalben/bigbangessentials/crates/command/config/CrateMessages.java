package com.pedrodalben.bigbangessentials.crates.command.config;

public final class CrateMessages {
    public static final String NO_PERMISSION = "\u00a7cVoc\u00ea n\u00e3o tem permiss\u00e3o para isso.";
    public static final String CRATE_DISABLED = "\u00a7cEsta crate est\u00e1 desabilitada.";
    public static final String CRATE_INVALID_ID = "\u00a7cID da crate inv\u00e1lido. Use apenas letras min\u00fasculas, n\u00fameros, '_'" +
        " e '-'.";
    public static final String CRATE_ALREADY_EXISTS = "\u00a7cJ\u00e1 existe uma crate com o ID '%s'.";
    public static final String CRATE_CREATED = "\u00a7aCrate '%s' criada com nome '%s'.";
    public static final String CRATE_INVALID_TARGET = "\u00a7cN\u00e3o foi poss\u00edvel encontrar o bloco alvo.";
    public static final String KEY_INVALID = "\u00a7cChave inv\u00e1lida.";
    public static final String KEY_ALREADY_EXISTS = "\u00a7cJ\u00e1 existe uma chave com o ID '%s'.";
    public static final String KEY_CREATED = "\u00a7aChave '%s' criada com nome '%s'.";
    public static final String KEY_TYPE_INVALID = "\u00a7cTipo de chave inv\u00e1lido. Use 'virtual' ou 'physical'.";
    public static final String KEY_COMMAND_INVALID = "\u00a7cO comando da chave n\u00e3o pode ficar em branco.";
    public static final String KEY_INSUFFICIENT = "\u00a7cVoc\u00ea n\u00e3o tem chaves suficientes.";
    public static final String RARITY_INVALID = "\u00a7cID da raridade inv\u00e1lido. Use apenas letras min\u00fasculas, n\u00fameros, '_' e '-'.";
    public static final String RARITY_ALREADY_EXISTS = "\u00a7cJ\u00e1 existe uma raridade com o ID '%s'.";
    public static final String RARITY_NOT_FOUND = "\u00a7cRaridade n\u00e3o encontrada: %s";
    public static final String REWARD_INVALID = "\u00a7cID da recompensa inv\u00e1lido. Use apenas letras min\u00fasculas, n\u00fameros, '_' e '-'.";
    public static final String REWARD_ALREADY_EXISTS = "\u00a7cJ\u00e1 existe uma recompensa com o ID '%s'.";
    public static final String REWARD_NOT_FOUND = "\u00a7cRecompensa n\u00e3o encontrada: %s";
    public static final String REWARD_TYPE_INVALID = "\u00a7cTipo de recompensa inv\u00e1lido. Use ITEM ou COMMAND.";
    public static final String REWARD_COMMAND_INVALID = "\u00a7cO comando da recompensa n\u00e3o pode ficar em branco.";
    public static final String MILESTONE_INVALID = "\u00a7cID do milestone inv\u00e1lido. Use apenas letras min\u00fasculas, n\u00fameros, '_' e '-'.";
    public static final String MILESTONE_ALREADY_EXISTS = "\u00a7cJ\u00e1 existe um milestone com o ID '%s'.";
    public static final String MILESTONE_NOT_FOUND = "\u00a7cMilestone n\u00e3o encontrado: %s";
    public static final String OPENING_TYPE_INVALID = "\u00a7cTipo de abertura inv\u00e1lido. Use: NONE, VIRTUAL ou PHYSICAL.";
    public static final String ITEM_REQUIRED = "\u00a7cVoc\u00ea precisa estar segurando um item na m\u00e3o principal.";
    public static final String COOLDOWN = "\u00a7cAguarde %s antes de abrir novamente.";
    public static final String REWARD_UNAVAILABLE = "\u00a7cNenhuma recompensa dispon\u00edvel no momento.";
    public static final String LIMIT_REACHED = "\u00a7cVoc\u00ea atingiu o limite desta recompensa.";
    public static final String INVENTORY_FULL = "\u00a7cSeu invent\u00e1rio est\u00e1 cheio.";
    public static final String DATABASE_UNAVAILABLE = "\u00a7cO banco de dados das crates est\u00e1 indispon\u00edvel no momento.";
    public static final String OPENING_STARTED = "\u00a7aAbrindo crate...";
    public static final String OPENING_COMPLETED = "\u00a7aVoc\u00ea recebeu: \u00a7f%s";
    public static final String MILESTONE_COMPLETED = "\u00a76\u00a7lMarco atingido! \u00a7e%s";
    public static final String CRATE_LINKED = "\u00a7aCrate vinculada ao bloco com sucesso.";
    public static final String CRATE_UNLINKED = "\u00a7cCrate desvinculada do bloco.";
    public static final String INVALID_ITEM = "\u00a7cItem inv\u00e1lido para esta crate.";
    public static final String INTERNAL_ERROR = "\u00a7cOcorreu um erro interno. Avise um administrador.";
    public static final String OPERATION_IN_PROGRESS = "\u00a7cVoc\u00ea j\u00e1 est\u00e1 abrindo uma crate.";
    public static final String MASS_OPEN_COMPLETED = "\u00a7aAbertura em massa conclu\u00edda: %d aberturas, %d recompensas.";
    public static final String MASS_OPEN_PARTIAL = "\u00a7eAbertura em massa interrompida ap\u00f3s %d de %d aberturas: %s";
    public static final String CLAIM_NO_PENDING = "\u00a7eVoc\u00ea n\u00e3o tem entregas pendentes.";
    public static final String CLAIM_SUCCESS = "\u00a7aVoc\u00ea resgatou %d %s da sua caixa de entregas.";
    public static final String RELOAD_COMPLETED = "\u00a7aM\u00f3dulo de Crates recarregado com sucesso.";
    public static final String PLAYER_ONLY = "\u00a7cEste comando s\u00f3 pode ser executado por jogadores.";
    public static final String CRATE_NOT_FOUND = "\u00a7cCrate n\u00e3o encontrada: %s";
    public static final String KEY_NOT_FOUND = "\u00a7cChave n\u00e3o encontrada: %s";
    public static final String PLAYER_NOT_FOUND = "\u00a7cJogador n\u00e3o encontrado.";
    public static final String CREATE_USAGE = "\u00a77Use: \u00a7e/crate create <id> [nome de exibi\u00e7\u00e3o]\n"
        + "\u00a77Exemplo: \u00a7e/crate create minha_crate \"Minha Crate\"";
    public static final String KEY_CREATE_USAGE = "\u00a77Use: \u00a7e/crate key create <id> [nome]\n"
        + "\u00a77Exemplo: \u00a7e/crate key create chave_vip \"Chave VIP\"";
    public static final String GIVE_SUCCESS = "\u00a7a%dx chave(s) '%s' fornecida(s) para %s.";
    public static final String GIVE_RECEIVE = "\u00a7aVoc\u00ea recebeu %dx chave(s) '%s'.";
    public static final String TAKE_SUCCESS = "\u00a7a%dx chave(s) '%s' removida(s) de %s.";
    public static final String KEY_USE_ONLY_ON_CRATE = "\u00a7cVoc\u00ea s\u00f3 pode usar esta chave em uma crate.";
    public static final String CRATE_REQUIRES_KEY = "\u00a7cEsta crate precisa da chave \u00a76%s \u00a7cpara abrir.";
    public static final String CRATE_NO_REWARDS = "\u00a7cEsta crate n\u00e3o tem recompensas dispon\u00edveis.";
    public static final String CRATE_OPENED = "\u00a7aVoc\u00ea abriu a crate \u00a76%s \u00a7ae recebeu: \u00a7f%s";
    public static final String CRATE_OPEN_FAILED = "\u00a7cN\u00e3o foi poss\u00edvel abrir a crate: %s";

    private CrateMessages() {}
}
