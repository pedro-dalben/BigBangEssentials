package com.pedrodalben.bigbangessentials.jobs.rewards;

import com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantResult;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateKeyGrantSource;
import com.pedrodalben.bigbangessentials.jobs.crates.CrateRewardGateway;
import com.pedrodalben.bigbangessentials.jobs.crates.DefaultCrateRewardGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class FragmentExchangeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FragmentExchangeService.class);
    private static final FragmentExchangeService INSTANCE = new FragmentExchangeService();

    public static FragmentExchangeService getInstance() {
        return INSTANCE;
    }

    private FragmentExchangeService() {}

    public int getExchangeRate() {
        return 12; // 12 Fragments -> 1 craft_key
    }

    public boolean canExchange(UUID playerId, int keysWanted) {
        if (playerId == null || keysWanted <= 0) return false;
        long required = (long) keysWanted * getExchangeRate();
        return JourneyFragmentService.getInstance().getBalance(playerId) >= required;
    }

    public ExchangeResult exchangeFragmentsForKey(UUID playerId, int keysWanted) {
        if (playerId == null || keysWanted <= 0) {
            return new ExchangeResult(false, 0, 0, "Invalid parameters");
        }
        long requiredFragments = (long) keysWanted * getExchangeRate();
        long currentBalance = JourneyFragmentService.getInstance().getBalance(playerId);
        if (currentBalance < requiredFragments) {
            return new ExchangeResult(false, 0, currentBalance, "Saldo insuficiente de Fragmentos de Jornada (" + currentBalance + "/" + requiredFragments + ")");
        }

        // Deduct fragments
        boolean removed = JourneyFragmentService.getInstance().removeFragments(
            playerId, requiredFragments, "FRAGMENT_EXCHANGE", "exchange_craft_key_" + keysWanted, "Converted to " + keysWanted + "x Craft Key"
        );
        if (!removed) {
            return new ExchangeResult(false, 0, currentBalance, "Falha ao deduzir Fragmentos de Jornada");
        }

        // Grant virtual key via CrateRewardGateway
        CrateRewardGateway gateway = DefaultCrateRewardGateway.getInstance();
        CrateKeyGrantResult grantResult = gateway.grantVirtualKey(
            playerId, "craft_key", keysWanted, CrateKeyGrantSource.FRAGMENT_EXCHANGE, "exchange_" + UUID.randomUUID(), null
        );

        if (!grantResult.success()) {
            // Rollback fragments if key grant failed
            JourneyFragmentService.getInstance().addFragments(
                playerId, requiredFragments, "ROLLBACK", "rollback_exchange_craft_key", null, null, null, "Rollback due to key grant failure: " + grantResult.errorMessage()
            );
            return new ExchangeResult(false, 0, currentBalance, "Falha ao conceder Chave do Ofício: " + grantResult.errorMessage());
        }

        long newBalance = JourneyFragmentService.getInstance().getBalance(playerId);
        LOGGER.info("Player {} successfully exchanged {} fragments for {}x craft_key. New balance: {}", playerId, requiredFragments, keysWanted, newBalance);
        JobRewardNotificationService.getInstance().notifyKeyExchanged(playerId, keysWanted, "craft_key");
        return new ExchangeResult(true, keysWanted, newBalance, "Conversão realizada com sucesso! +" + keysWanted + "x Chave do Ofício.");
    }

    public record ExchangeResult(boolean success, int keysGranted, long newFragmentBalance, String message) {}
}
