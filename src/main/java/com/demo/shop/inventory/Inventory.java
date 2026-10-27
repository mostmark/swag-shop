package com.demo.shop.inventory;

/** Stock levels, and reservation of stock when an order is placed. */
public interface Inventory {

    int INITIAL_STOCK = 500;
    int RESTOCK_QUANTITY = 500;

    record Reservation(String productId, int quantity, int remaining) {
    }

    Reservation reserve(String productId, int quantity);

    int available(String productId);
}
