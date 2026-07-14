package com.pedrodalben.bigbangessentials.jobs.feedback;

public enum EarningsFeedbackMode {
    ACTION_BAR,
    CHAT,
    BOSS_BAR,
    NONE;

    public static EarningsFeedbackMode fromString(String s) {
        if (s == null) return ACTION_BAR;
        try { return valueOf(s.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { return ACTION_BAR; }
    }
}
