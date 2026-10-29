package com.demo.shop.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import com.demo.shop.catalog.ProductCatalog;

@Path("/api/prices")
public class PriceResource {

    /** Number of days of price history sent to the browser for the price chart. */
    static final int CHART_DAYS = 90;

    public record Price(String productId, BigDecimal price, BigDecimal lowestLast30Days,
            List<ChartPoint> priceHistory) {
    }

    public record ChartPoint(LocalDate date, BigDecimal price) {
    }

    @Inject
    ProductCatalog catalog;

    @Inject
    PriceService priceService;

    /** The frontend passes on the shopper's X-Request-Id, which the leaking cache uses in its key. */
    @GET
    @Path("/{productId}")
    public Price price(@PathParam("productId") String productId, @HeaderParam("X-Request-Id") String requestId) {
        if (catalog.findById(productId).isEmpty()) {
            throw new NotFoundException("Unknown product " + productId);
        }
        PriceQuote quote = priceService.quote(productId,
                requestId != null ? requestId : UUID.randomUUID().toString());
        List<PricePoint> history = quote.history();
        List<ChartPoint> chart = history.subList(Math.max(0, history.size() - CHART_DAYS), history.size()).stream()
                .map(point -> new ChartPoint(point.date(), point.price()))
                .toList();
        return new Price(productId, quote.price(), quote.lowestLast30Days(), chart);
    }
}
