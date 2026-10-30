package com.demo.shop.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.demo.shop.catalog.Product;
import com.demo.shop.catalog.ProductCatalog;
import com.demo.shop.order.InventoryClient.ReservationRequest;

@ApplicationScoped
public class CheckoutService {

    public static final int MAX_QUANTITY = 10;

    /**
     * Orders are processed by at most this many requests at the same time; the others wait in line, first come,
     * first served. That keeps a rush of orders from flooding the inventory service, but while the orders in
     * progress are stuck, every new order waits behind them.
     */
    static final int MAX_CONCURRENT_CHECKOUTS = 8;

    public record OrderLine(String productId, int quantity) {
    }

    public record OrderItem(String productId, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record Order(String orderId, List<OrderItem> items, BigDecimal total, Instant placedAt) {
    }

    private final Semaphore checkoutSlots = new Semaphore(MAX_CONCURRENT_CHECKOUTS, true);
    private final AtomicLong ordersPlaced = new AtomicLong();

    @Inject
    ProductCatalog catalog;

    @Inject
    @RestClient
    InventoryClient inventory;

    public Order checkout(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("The cart is empty");
        }
        List<Product> products = new ArrayList<>();
        for (OrderLine line : lines) {
            if (line.quantity() < 1 || line.quantity() > MAX_QUANTITY) {
                throw new IllegalArgumentException("Quantity must be between 1 and " + MAX_QUANTITY);
            }
            products.add(catalog.findById(line.productId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown product " + line.productId())));
        }

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        checkoutSlots.acquireUninterruptibly();
        try {
            for (int i = 0; i < lines.size(); i++) {
                OrderLine line = lines.get(i);
                Product product = products.get(i);
                inventory.reserve(new ReservationRequest(product.id(), line.quantity()));
                BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(line.quantity()));
                items.add(new OrderItem(product.id(), product.name(), line.quantity(), product.price(), lineTotal));
                total = total.add(lineTotal);
            }
        } finally {
            checkoutSlots.release();
        }
        ordersPlaced.incrementAndGet();
        String orderId = "SW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new Order(orderId, items, total, Instant.now());
    }

    public long ordersPlaced() {
        return ordersPlaced.get();
    }
}
