package com.demo.shop.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.demo.shop.catalog.ProductCatalog;

class InventoryTest {

    private static final WarehouseClient INSTANT_WAREHOUSE = new WarehouseClient() {
        @Override
        public void confirmReservation(String productId, int quantity) {
        }
    };

    private static Inventory create(String type) {
        ProductCatalog catalog = new ProductCatalog();
        return type.equals("synchronized")
                ? new SynchronizedInventory(catalog, INSTANT_WAREHOUSE)
                : new PerProductLockInventory(catalog, INSTANT_WAREHOUSE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "synchronized", "per-product" })
    void reservingReducesStock(String type) {
        Inventory inventory = create(type);
        Inventory.Reservation reservation = inventory.reserve("red-fedora", 3);
        assertEquals(Inventory.INITIAL_STOCK - 3, reservation.remaining());
        assertEquals(Inventory.INITIAL_STOCK - 3, inventory.available("red-fedora"));
        assertEquals(Inventory.INITIAL_STOCK, inventory.available("sticker-pack"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "synchronized", "per-product" })
    void restocksInsteadOfRunningOut(String type) {
        Inventory inventory = create(type);
        inventory.reserve("sticker-pack", Inventory.INITIAL_STOCK);
        Inventory.Reservation reservation = inventory.reserve("sticker-pack", 2);
        assertEquals(Inventory.RESTOCK_QUANTITY - 2, reservation.remaining());
    }

    @ParameterizedTest
    @ValueSource(strings = { "synchronized", "per-product" })
    void concurrentReservationsAreNotLost(String type) throws Exception {
        Inventory inventory = create(type);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<Inventory.Reservation>> futures = IntStream.range(0, 200)
                    .mapToObj(i -> pool.submit(() -> inventory.reserve("red-t-shirt", 1)))
                    .toList();
            for (Future<Inventory.Reservation> future : futures) {
                future.get();
            }
        }
        assertEquals(Inventory.INITIAL_STOCK - 200, inventory.available("red-t-shirt"));
    }
}
