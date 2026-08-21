package com.pedrodalben.bigbangessentials.crates.domain;

/**
 * Type of crate opening animation/mode.
 */
public enum CrateOpeningType {
    /**
     * No animation - instant reward delivery.
     * Used for commands, events, and mass open.
     */
    NONE,
    
    /**
     * Virtual GUI animation - player-specific animated GUI.
     * Rolling items, central highlight, configurable sounds/particles.
     */
    VIRTUAL,
    
    /**
     * Physical block animation - effects at the crate block location.
     * Visible to nearby players, particles/sounds at block.
     */
    PHYSICAL
}