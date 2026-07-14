package com.pedrodalben.bigbangessentials.jobs.availability;

public record JobRequirementResult(
    String id,
    JobRequirementType type,
    boolean completed,
    String title,
    String description,
    String expectedValue,
    String currentValue,
    String actionId
) {
    public static final String NO_ACTION = "NONE";

    public boolean hasAction() {
        return actionId != null && !actionId.equals(NO_ACTION) && !actionId.isBlank();
    }
}
