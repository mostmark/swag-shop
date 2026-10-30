package com.demo.shop.inventory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.demo.shop.catalog.ProductCatalog;

/**
 * Inventory made thread safe the quick way: the whole reserve method is synchronized.
 *
 * <p>
 * The bug: one lock covers every product, and it is held during the slow call to the warehouse. Only one
 * checkout in the whole shop can make progress at a time; all the others wait in line for the monitor.
 */
@ApplicationScoped
public class SynchronizedInventory implements Inventory {

    private final Map<String, Integer> stock = new ConcurrentHashMap<>();
    private final WarehouseClient warehouse;

    @Inject
    public SynchronizedInventory(ProductCatalog catalog, WarehouseClient warehouse) {
        this.warehouse = warehouse;
        catalog.all().forEach(product -> stock.put(product.id(), INITIAL_STOCK));
    }

    @Override
    public synchronized Reservation reserve(String productId, int quantity) {
        int available = stock.get(productId);
        if (available < quantity) {
            available += RESTOCK_QUANTITY;
        }
        int remaining = available - quantity;
        stock.put(productId, remaining);
        warehouse.confirmReservation(productId, quantity);
        return new Reservation(productId, quantity, remaining);
    }

    @Override
    public int available(String productId) {
        return stock.getOrDefault(productId, 0);
    }
}
