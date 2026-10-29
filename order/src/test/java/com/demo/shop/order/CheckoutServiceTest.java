package com.demo.shop.order;

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
import com.demo.shop.order.CheckoutService.Order;
import com.demo.shop.order.CheckoutService.OrderLine;

class CheckoutServiceTest {

    @Test
    void processesAtMostEightOrdersAtOnce() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        InventoryClient slowInventory = request -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(20);
                return new InventoryClient.Reservation(request.productId(), request.quantity(), 0);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            } finally {
                running.decrementAndGet();
            }
        };
        CheckoutService checkout = new CheckoutService();
        checkout.catalog = ProductCatalog.bundled();
        checkout.inventory = slowInventory;
        List<Product> products = checkout.catalog.all();

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
