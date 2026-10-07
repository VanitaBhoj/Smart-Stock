package com.smartstock.exception;

public class InventoryReservationLockUnavailableException extends RuntimeException {
    public InventoryReservationLockUnavailableException(Throwable cause) {
        super("Inventory coordination is temporarily unavailable.", cause);
    }
}
