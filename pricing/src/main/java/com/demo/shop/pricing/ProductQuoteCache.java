package com.demo.shop.pricing;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * The fix: the quote is the same for everyone, so the key is the product id alone. Entries expire after a
 * minute and the cache has a fixed upper size, so it can never grow without limit.
 */
@ApplicationScoped
public class ProductQuoteCache implements QuoteCache {

    static final Duration TIME_TO_LIVE = Duration.ofMinutes(1);
    static final int MAX_ENTRIES = 1_000;

    private record Entry(PriceQuote quote, long expiresAtNanos) {

        boolean isExpired() {
            return System.nanoTime() - expiresAtNanos > 0;
        }
    }

    private final Map<String, Entry> quotes = new ConcurrentHashMap<>();

    @Override
    public PriceQuote get(String productId, String requestId, Function<String, PriceQuote> calculator) {
        Entry entry = quotes.get(productId);
        if (entry != null && !entry.isExpired()) {
            return entry.quote();
        }
        PriceQuote quote = calculator.apply(productId);
        if (entry != null || quotes.size() < MAX_ENTRIES) {
            quotes.put(productId, new Entry(quote, System.nanoTime() + TIME_TO_LIVE.toNanos()));
        }
        return quote;
    }

    @Override
    public int size() {
        return quotes.size();
    }

    @Override
    public void clear() {
        quotes.clear();
    }
}
