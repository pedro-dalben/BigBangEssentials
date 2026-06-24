package com.pedrodalben.bigbangessentials.database.exception;

/**
 * Thrown when an error occurs during database migration.
 */
public class MigrationException extends DatabaseException {
    public MigrationException(String message) {
        super(message);
    }

    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
