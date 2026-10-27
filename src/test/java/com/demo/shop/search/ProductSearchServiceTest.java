package com.demo.shop.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.demo.shop.catalog.ProductCatalog;

class ProductSearchServiceTest {

    private final RecursiveEditDistance recursive = new RecursiveEditDistance();
    private final IterativeEditDistance iterative = new IterativeEditDistance();
    private final ProductSearchService search = new ProductSearchService(new ProductCatalog(), null, recursive,
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

    private static List<String> ids(List<ProductSearchService.SearchHit> hits) {
        return hits.stream().map(hit -> hit.product().id()).toList();
    }
}
