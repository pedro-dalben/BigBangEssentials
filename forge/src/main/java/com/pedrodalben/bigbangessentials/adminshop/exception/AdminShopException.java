package com.pedrodalben.bigbangessentials.adminshop.exception;

public class AdminShopException extends RuntimeException {
    public AdminShopException(String message) {
        super(message);
    }
    public AdminShopException(String message, Throwable cause) {
        super(message, cause);
    }
}
