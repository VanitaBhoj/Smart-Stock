package com.smartstock.exception;

public class ReservationStateException extends InvalidStateTransitionException {

    public ReservationStateException(String message) {
        super(message);
    }
}
