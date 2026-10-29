package com.demo.shop.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.demo.shop.catalog.Product;
import com.demo.shop.catalog.ProductCatalog;
import com.demo.shop.checkout.CheckoutService.Order;
import com.demo.shop.checkout.CheckoutService.OrderLine;
import com.demo.shop.demo.DemoSettings;
import com.demo.shop.demo.Scenario;
import com.demo.shop.inventory.PerProductLockInventory;
import com.demo.shop.inventory.WarehouseClient;

class CheckoutServiceTest {

    @Test
    void processesAtMostEightOrdersAtOnce() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        WarehouseClient slowWarehouse = new WarehouseClient() {
            @Override
            public void confirmReservation(String productId, int quantity) {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                } finally {
                    running.decrementAndGet();
                }
            }
        };
        ProductCatalog catalog = new ProductCatalog();
        CheckoutService checkout = new CheckoutService();
        checkout.catalog = catalog;
        checkout.perProductLockInventory = new PerProductLockInventory(catalog, slowWarehouse);
        checkout.settings = new DemoSettings() {
            @Override
            public boolean problemEnabled(Scenario scenario) {
                return false;
            }
        };
        List<Product> products = catalog.all();

        try (ExecutorService shoppers = Executors.newFixedThreadPool(20)) {
            List<Future<Order>> orders = IntStream.range(0, 40)
                    .mapToObj(i -> shoppers.submit(() -> checkout.checkout(
                            List.of(new OrderLine(products.get(i % products.size()).id(), 1)))))
                    .toList();
            for (Future<Order> order : orders) {
                assertEquals(1, order.get().items().size());
            }
        }
        assertEquals(CheckoutService.MAX_CONCURRENT_CHECKOUTS, maxRunning.get());
        assertEquals(40, checkout.ordersPlaced());
    }
}
