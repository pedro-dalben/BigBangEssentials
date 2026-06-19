package com.pedrodalben.bigbangessentials.database.exception;

/**
 * Base exception for database-related issues.
 */
public class DatabaseException extends RuntimeException {
    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
