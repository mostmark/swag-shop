package com.demo.shop.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

import com.demo.shop.catalog.Product;
import com.demo.shop.catalog.ProductCatalog;
import com.demo.shop.checkout.CheckoutService;
import com.demo.shop.pricing.PricePoint;
import com.demo.shop.pricing.PriceQuote;
import com.demo.shop.pricing.PriceService;
import com.demo.shop.search.ProductSearchService;

@Path("/api")
public class ProductResource {

    /** Number of days of price history sent to the browser for the price chart. */
    static final int CHART_DAYS = 90;

    public record ProductDetails(Product product, BigDecimal price, BigDecimal lowestLast30Days, int inStock,
            List<ChartPoint> priceHistory) {
    }

    public record ChartPoint(LocalDate date, BigDecimal price) {
    }

    public record SearchResult(String query, int count, List<Product> products) {
    }

    @Inject
    ProductCatalog catalog;

    @Inject
    PriceService priceService;

    @Inject
    ProductSearchService searchService;

    @Inject
    CheckoutService checkoutService;

    @GET
    @Path("/products")
    public List<Product> products() {
        return catalog.all();
    }

    @GET
    @Path("/products/{id}")
    public ProductDetails product(@PathParam("id") String id, @HeaderParam("X-Request-Id") String requestId) {
        Product product = catalog.findById(id).orElseThrow(() -> new NotFoundException("Unknown product " + id));
        PriceQuote quote = priceService.quote(id, requestId != null ? requestId : UUID.randomUUID().toString());
        List<PricePoint> history = quote.history();
        List<ChartPoint> chart = history.subList(Math.max(0, history.size() - CHART_DAYS), history.size()).stream()
                .map(point -> new ChartPoint(point.date(), point.price()))
                .toList();
        int inStock = checkoutService.activeInventory().available(id);
        return new ProductDetails(product, quote.price(), quote.lowestLast30Days(), inStock, chart);
    }

    @GET
    @Path("/search")
    public SearchResult search(@QueryParam("q") String query) {
        List<Product> products = searchService.search(query).stream()
                .map(ProductSearchService.SearchHit::product)
                .toList();
        return new SearchResult(query == null ? "" : query, products.size(), products);
    }
}
