package com.demo.shop.pricing;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** Size of the leaking price cache, for the pricing card on the demo control page. */
@Path("/api/demo/leak")
public class LeakResource {

    public record LeakStats(int entries, long maxEntries, long estimatedMb) {
    }

    @Inject
    PriceService priceService;

    @GET
    public LeakStats leak() {
        RequestQuoteCache leakingCache = priceService.requestQuoteCache();
        return new LeakStats(leakingCache.size(), leakingCache.maxEntries(),
                leakingCache.estimatedBytes() / (1024 * 1024));
    }
}
