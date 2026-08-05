package com.pedrodalben.bigbangessentials.menu.integration.economy;

import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.menu.integration.economy.provider.GemsTopMenuDataProvider;
import com.pedrodalben.bigbangessentials.menu.integration.economy.provider.MoneyTopMenuDataProvider;

public class EconomyMenuIntegration {

    public void initialize() {
        // Register data providers
        MenuSystem.getInstance().getDataProviderRegistry().registerProvider("economy.top.money", new MoneyTopMenuDataProvider());
        MenuSystem.getInstance().getDataProviderRegistry().registerProvider("economy.top.gems", new GemsTopMenuDataProvider());
    }
}
