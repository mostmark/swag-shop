package com.demo.shop.pricing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Price quote cache whose key includes the request id, so every quote can be traced back to the request that
 * created it.
 *
 * <p>
 * The bug: request ids are unique, so the cache never gets a hit and nothing is ever removed. Every product
 * page view adds another quote, with its full price history, that stays on the heap for good.
 */
@ApplicationScoped
public class RequestQuoteCache implements QuoteCache {

    record QuoteKey(String productId, String requestId) {
    }

    private final Map<QuoteKey, PriceQuote> quotes = new ConcurrentHashMap<>();
    private final long maxEntries;

    @Inject
    public RequestQuoteCache(@ConfigProperty(name = "pricing.leak-max-heap-ratio") double leakMaxHeapRatio) {
        // Demo safety net, not part of the bug: stop growing before the heap runs out
        this.maxEntries = (long) (Runtime.getRuntime().maxMemory() * leakMaxHeapRatio
                / PriceQuote.ESTIMATED_SIZE_BYTES);
    }

    @Override
    public PriceQuote get(String productId, String requestId, Function<String, PriceQuote> calculator) {
        QuoteKey key = new QuoteKey(productId, requestId);
        PriceQuote cached = quotes.get(key);
        if (cached != null) {
            return cached;
        }
        PriceQuote quote = calculator.apply(productId);
        if (quotes.size() < maxEntries) {
            quotes.put(key, quote);
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

    public long maxEntries() {
        return maxEntries;
    }

    public long estimatedBytes() {
        return quotes.size() * PriceQuote.ESTIMATED_SIZE_BYTES;
    }
}
