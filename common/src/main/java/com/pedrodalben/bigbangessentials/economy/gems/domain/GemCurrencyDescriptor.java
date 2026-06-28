package com.pedrodalben.bigbangessentials.economy.gems.domain;

public record GemCurrencyDescriptor(
    String technicalId,
    String symbol,
    String singular,
    String plural,
    boolean symbolBeforeAmount,
    String thousandsSeparator
) {}
