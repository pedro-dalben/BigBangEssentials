package com.pedrodalben.bigbangessentials.database;

/**
 * State of the database manager.
 */
public enum DatabaseState {
    NEW,
    STARTING,
    MIGRATING,
    READY,
    DEGRADED,
    FAILED,
    STOPPING,
    STOPPED
}
