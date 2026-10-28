package com.demo.shop.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.demo.shop.catalog.Product;
import com.demo.shop.catalog.ProductCatalog;
import com.demo.shop.demo.DemoSettings;
import com.demo.shop.demo.Scenario;

/**
 * Typo-tolerant product search: "backpak" finds the backpack and "hoody" finds the hoodie. Adjacent word pairs
 * are indexed as well, so "tshirt" finds the "T-Shirt" and "waterbottle" finds the "Water Bottle". Every
 * search term is compared with every indexed word of every product using the edit distance.
 */
@ApplicationScoped
public class ProductSearchService {

    /** Longer indexed words are cut to this length before they are compared. */
    static final int MAX_WORD_LENGTH = 10;

    /** Longer search terms are cut to this length; the shorter prefix still finds the right products. */
    static final int MAX_TERM_LENGTH = 10;

    public record SearchHit(Product product, int typos) {
    }

    private final ProductCatalog catalog;
    private final DemoSettings settings;
    private final EditDistance recursive;
    private final EditDistance iterative;
    private final Map<String, List<String>> wordsByProduct;

    @Inject
    public ProductSearchService(ProductCatalog catalog, DemoSettings settings, RecursiveEditDistance recursive,
            IterativeEditDistance iterative) {
        this.catalog = catalog;
        this.settings = settings;
        this.recursive = recursive;
        this.iterative = iterative;
        this.wordsByProduct = catalog.all().stream()
                .collect(Collectors.toMap(Product::id, ProductSearchService::searchableWords));
    }

    public List<SearchHit> search(String query) {
        EditDistance editDistance = settings.problemEnabled(Scenario.SEARCH) ? recursive : iterative;
        return search(query, editDistance);
    }

    List<SearchHit> search(String query, EditDistance editDistance) {
        List<String> terms = splitWords(query == null ? "" : query)
                .map(term -> term.length() > MAX_TERM_LENGTH ? term.substring(0, MAX_TERM_LENGTH) : term)
                .toList();
        if (terms.isEmpty()) {
            return catalog.all().stream().map(p -> new SearchHit(p, 0)).toList();
        }
        List<SearchHit> hits = new ArrayList<>();
        for (Product product : catalog.all()) {
            int totalTypos = 0;
            boolean allTermsFound = true;
            for (String term : terms) {
                int typos = closestMatch(term, wordsByProduct.get(product.id()), editDistance);
                if (typos > allowedTypos(term)) {
                    allTermsFound = false;
                    break;
                }
                totalTypos += typos;
            }
            if (allTermsFound) {
                hits.add(new SearchHit(product, totalTypos));
            }
        }
        hits.sort(Comparator.comparingInt(SearchHit::typos).thenComparing(hit -> hit.product().name()));
        return hits;
    }

    private static int closestMatch(String term, List<String> words, EditDistance editDistance) {
        int best = Integer.MAX_VALUE;
        for (String word : words) {
            int distance = word.startsWith(term) ? 0 : editDistance.distance(term, word);
            best = Math.min(best, distance);
        }
        return best;
    }

    private static int allowedTypos(String term) {
        if (term.length() <= 3) {
            return 0;
        }
        return term.length() <= 6 ? 1 : 2;
    }

    private static List<String> searchableWords(Product product) {
        return Stream.concat(Stream.of(product.name(), product.category(), product.description()),
                product.tags().stream())
                .flatMap(ProductSearchService::wordsAndPairs)
                .filter(word -> word.length() >= 3)
                .map(ProductSearchService::truncate)
                .distinct()
                .toList();
    }

    private static Stream<String> wordsAndPairs(String text) {
        List<String> words = splitWords(text).toList();
        Stream<String> pairs = IntStream.range(0, Math.max(0, words.size() - 1))
                .mapToObj(i -> words.get(i) + words.get(i + 1));
        return Stream.concat(words.stream(), pairs);
    }

    private static Stream<String> splitWords(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")).filter(word -> !word.isEmpty());
    }

    private static String truncate(String word) {
        return word.length() > MAX_WORD_LENGTH ? word.substring(0, MAX_WORD_LENGTH) : word;
    }
}
