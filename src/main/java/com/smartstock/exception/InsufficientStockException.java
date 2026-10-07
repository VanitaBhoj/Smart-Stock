package com.smartstock.exception;

public class InsufficientStockException extends InsufficientInventoryException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
