package com.zerog.bigbangessentials.api;

import com.zerog.bigbangessentials.BigBangEssentialsManager;
import com.zerog.bigbangessentials.api.economy.EconomyService;

/**
 * Main API entrypoint for BigBangEssentials mod interoperability.
 */
public class BigBangEssentialsAPI {
    public static final String API_VERSION = "1.0.0";

    /**
     * Checks if the BigBangEssentials API is available for use by other mods.
     * @return true if available
     */
    public static boolean isAvailable() {
        return true;
    }

    /**
     * Provides access to the global EconomyService instance.
     * <p>
     * Usage:
     * <pre>
     * import com.zerog.bigbangessentials.api.BigBangEssentialsAPI;
     * import com.zerog.bigbangessentials.api.economy.EconomyService;
     * EconomyService eco = BigBangEssentialsAPI.getEconomyService();
     * </pre>
     * @return the singleton EconomyService instance
     */
    public static EconomyService getEconomyService() {
        return BigBangEssentialsManager.getInstance().getEconomyService();
    }

    // Future API methods for mod interoperability will be added here.
}
