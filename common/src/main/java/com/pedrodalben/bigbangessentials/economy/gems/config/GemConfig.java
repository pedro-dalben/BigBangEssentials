package com.pedrodalben.bigbangessentials.economy.gems.config;

public class GemConfig {
    public boolean enabled = true;
    public String technicalId = "gem";
    public Display display = new Display();
    public PersistenceConfig persistence = new PersistenceConfig();
    public ReservationsConfig reservations = new ReservationsConfig();
    public BalancesConfig balances = new BalancesConfig();
    public CommandsConfig commands = new CommandsConfig();
    
    public static class CommandsConfig {
        public String root = "gemas";
    }
    
    public static class Display {
        public String name = "Gemas";
        public String symbol = "✦";
        public String singular = "Gema";
        public String plural = "Gemas";
        public boolean symbolBeforeAmount = true;
        public String thousandsSeparator = ".";
    }
    
    public static class BalancesConfig {
        public long startingBalance = 0;
        public long maxBalance = Long.MAX_VALUE;
        public boolean allowNegativeBalances = false;
    }
    
    public static class PersistenceConfig {
        public boolean createBackups = false;
        public int maxTransactionLogEntries = 1000;
    }
    
    public static class ReservationsConfig {
        public boolean enabled = true;
        public long maxLeaseSeconds = 86400;
        public long defaultLeaseSeconds = 3600;
        public boolean allowExternalRenewal = true;
        public long cleanupIntervalSeconds = 300;
    }
}
