package com.smartstock.exception;

public class OrderStateException extends InvalidStateTransitionException {

    public OrderStateException(String message) {
        super(message);
    }
}
