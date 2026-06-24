package com.pedrodalben.bigbangessentials.database.exception;

/**
 * Thrown when operations are attempted but the database is not available.
 */
public class DatabaseUnavailableException extends DatabaseException {
    public DatabaseUnavailableException(String message) {
        super(message);
    }

    public DatabaseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
