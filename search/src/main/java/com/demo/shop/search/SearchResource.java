package com.demo.shop.search;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import com.demo.shop.catalog.Product;

@Path("/api/search")
public class SearchResource {

    public record SearchResult(String query, int count, List<Product> products) {
    }

    @Inject
    ProductSearchService searchService;

    @GET
    public SearchResult search(@QueryParam("q") String query) {
        List<Product> products = searchService.search(query).stream()
                .map(ProductSearchService.SearchHit::product)
                .toList();
        return new SearchResult(query == null ? "" : query, products.size(), products);
    }
}
