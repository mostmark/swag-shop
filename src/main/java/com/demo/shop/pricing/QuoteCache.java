package com.demo.shop.pricing;

import java.util.function.Function;

/** Keeps calculated price quotes so the price history is not recalculated for every page view. */
public interface QuoteCache {

    PriceQuote get(String productId, String requestId, Function<String, PriceQuote> calculator);

    int size();

    void clear();
}
