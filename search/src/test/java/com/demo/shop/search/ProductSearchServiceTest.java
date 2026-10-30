package com.demo.shop.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.demo.shop.catalog.ProductCatalog;
import com.demo.shop.demo.DemoSettings;

class ProductSearchServiceTest {

    private final RecursiveEditDistance recursive = new RecursiveEditDistance();
    private final IterativeEditDistance iterative = new IterativeEditDistance();
    private final ProductSearchService search = new ProductSearchService(ProductCatalog.bundled(), null, recursive,
            iterative);

    @ParameterizedTest
    @CsvSource({ "kitten,sitting,3", "flaw,lawn,2", "hoody,hoodie,2", "backpak,backpack,1", "same,same,0", ",abc,3" })
    void bothImplementationsComputeTheEditDistance(String a, String b, int expected) {
        String left = a == null ? "" : a;
        assertEquals(expected, recursive.distance(left, b));
        assertEquals(expected, iterative.distance(left, b));
    }

    @ParameterizedTest
    @ValueSource(strings = { "fedora", "red hat", "hoody", "backpak", "tshirt", "waterbottle", "coffee mug", "zzzz",
            "enamel pins", "" })
    void naiveAndFixedSearchReturnTheSameResults(String query) {
        assertEquals(ids(search.search(query, iterative)), ids(search.search(query, recursive)));
    }

    @ParameterizedTest
    @CsvSource({ "hoody,charcoal-zip-hoodie", "backpak,laptop-backpack", "tshirt,red-t-shirt",
            "waterbottle,red-water-bottle", "fedora,red-fedora" })
    void toleratesTyposAndJoinedWords(String query, String expectedFirst) {
        assertEquals(expectedFirst, search.search(query, iterative).get(0).product().id());
    }

    @Test
    void emptyQueryReturnsTheWholeCatalog() {
        assertEquals(8, search.search("  ", iterative).size());
    }

    @Test
    void unknownWordsFindNothing() {
        assertEquals(List.of(), search.search("xylophone", iterative));
    }

    @Test
    void runsAtMostThreeSearchesAtOnce() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        IterativeEditDistance slowFixed = new IterativeEditDistance() {
            @Override
            public int distance(String a, String b) {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(0, 200_000);
                    return super.distance(a, b);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                } finally {
                    running.decrementAndGet();
                }
            }
        };
        DemoSettings fixed = new DemoSettings() {
            @Override
            public boolean problemEnabled() {
                return false;
            }
        };
        ProductSearchService limited = new ProductSearchService(ProductCatalog.bundled(), fixed, recursive, slowFixed);

        try (ExecutorService shoppers = Executors.newFixedThreadPool(10)) {
            List<Future<List<ProductSearchService.SearchHit>>> searches = IntStream.range(0, 10)
                    .mapToObj(i -> shoppers.submit(() -> limited.search("xylophone")))
                    .toList();
            for (Future<List<ProductSearchService.SearchHit>> search : searches) {
                assertEquals(List.of(), search.get());
            }
        }
        assertEquals(ProductSearchService.MAX_CONCURRENT_SEARCHES, maxRunning.get());
    }

    private static List<String> ids(List<ProductSearchService.SearchHit> hits) {
        return hits.stream().map(hit -> hit.product().id()).toList();
    }
}
