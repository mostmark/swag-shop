package com.demo.shop.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import com.demo.shop.catalog.Product;
import com.demo.shop.catalog.ProductCatalog;
import com.demo.shop.demo.DemoSettings;
import com.demo.shop.demo.ScenarioChanged;

/** Calculates product prices from two years of price history (campaigns, seasonal changes). */
@ApplicationScoped
public class PriceService {

    static final int HISTORY_DAYS = 730;

    @Inject
    ProductCatalog catalog;

    @Inject
    DemoSettings settings;

    @Inject
    RequestQuoteCache requestQuoteCache;

    @Inject
    ProductQuoteCache productQuoteCache;

    public PriceQuote quote(String productId, String requestId) {
        return activeCache().get(productId, requestId, this::calculateQuote);
    }

    public QuoteCache activeCache() {
        return settings.problemEnabled() ? requestQuoteCache : productQuoteCache;
    }

    public RequestQuoteCache requestQuoteCache() {
        return requestQuoteCache;
    }

    PriceQuote calculateQuote(String productId) {
        Product product = catalog.findById(productId)
                .orElseThrow(() -> new NoSuchElementException("Unknown product " + productId));
        LocalDate today = LocalDate.now();
        int seed = Math.abs(productId.hashCode() % 97);
        List<PricePoint> history = new ArrayList<>(HISTORY_DAYS);
        for (int daysAgo = HISTORY_DAYS - 1; daysAgo >= 0; daysAgo--) {
            // Seasonal swings and short campaigns around today's list price
            double seasonal = 0.08 * (Math.sin((daysAgo + seed) / 45.0) - Math.sin(seed / 45.0));
            double campaign = daysAgo > 0 && (daysAgo + seed) % 120 < 10 ? -0.2 : 0.0;
            BigDecimal price = product.price()
                    .multiply(BigDecimal.valueOf(1 + seasonal + campaign))
                    .setScale(2, RoundingMode.HALF_UP);
            history.add(new PricePoint(today.minusDays(daysAgo), price));
        }
        BigDecimal lowestLast30Days = history.subList(HISTORY_DAYS - 30, HISTORY_DAYS).stream()
                .map(PricePoint::price)
                .min(Comparator.naturalOrder())
                .orElse(product.price());
        return new PriceQuote(productId, product.price(), lowestLast30Days, history, Instant.now());
    }

    void onScenarioChanged(@Observes ScenarioChanged event) {
        // Switching to the fixed code stands in for deploying the fix: the restarted pod starts with an empty heap
        if (!event.problemEnabled()) {
            requestQuoteCache.clear();
        }
    }
}
