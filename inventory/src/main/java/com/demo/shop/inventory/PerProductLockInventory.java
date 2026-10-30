package com.demo.shop.inventory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.demo.shop.catalog.ProductCatalog;

/**
 * The fix: each product has its own lock, and the lock only protects the stock update. The slow warehouse
 * call happens after the lock is released, so checkouts no longer queue behind each other.
 */
@ApplicationScoped
public class PerProductLockInventory implements Inventory {

    private final Map<String, Integer> stock = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final WarehouseClient warehouse;

    @Inject
    public PerProductLockInventory(ProductCatalog catalog, WarehouseClient warehouse) {
        this.warehouse = warehouse;
        catalog.all().forEach(product -> {
            stock.put(product.id(), INITIAL_STOCK);
            locks.put(product.id(), new ReentrantLock());
        });
    }

    @Override
    public Reservation reserve(String productId, int quantity) {
        int remaining;
        ReentrantLock lock = locks.get(productId);
        lock.lock();
        try {
            int available = stock.get(productId);
            if (available < quantity) {
                available += RESTOCK_QUANTITY;
            }
            remaining = available - quantity;
            stock.put(productId, remaining);
        } finally {
            lock.unlock();
        }
        warehouse.confirmReservation(productId, quantity);
        return new Reservation(productId, quantity, remaining);
    }

    @Override
    public int available(String productId) {
        return stock.getOrDefault(productId, 0);
    }
}
