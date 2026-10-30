package com.demo.shop.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The price shown on a product page, with the price history behind the "lowest price in 30 days" label and
 * the price chart.
 */
public record PriceQuote(
        String productId,
        BigDecimal price,
        BigDecimal lowestLast30Days,
        List<PricePoint> history,
        Instant quotedAt) {

    /** Rough heap size of one quote: about 90 bytes per price point plus the list. */
    static final long ESTIMATED_SIZE_BYTES = PriceService.HISTORY_DAYS * 96L + 512;
}
