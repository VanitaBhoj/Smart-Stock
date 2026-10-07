package com.smartstock.exception;

public class InventoryReservationLockBusyException extends RuntimeException {
    public InventoryReservationLockBusyException(Long productId) {
        super("Inventory for product " + productId + " is being updated; retry the request.");
    }
}
