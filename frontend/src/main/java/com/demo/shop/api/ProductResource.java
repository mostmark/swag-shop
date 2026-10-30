package com.demo.shop.api;

import java.math.BigDecimal;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.demo.shop.clients.CatalogClient;
import com.demo.shop.clients.InventoryClient;
import com.demo.shop.clients.PricingClient;
import com.demo.shop.clients.SearchClient;
import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;

/**
 * The shop's product API for the browser. The frontend does no work of its own: it calls the services without
 * blocking a thread, and combines their answers for the product page.
 */
@Path("/api")
public class ProductResource {

    public record ProductDetails(JsonNode product, BigDecimal price, BigDecimal lowestLast30Days, int inStock,
            JsonNode priceHistory) {
    }

    @Inject
    @RestClient
    CatalogClient catalog;

    @Inject
    @RestClient
    PricingClient pricing;

    @Inject
    @RestClient
    InventoryClient inventory;

    @Inject
    @RestClient
    SearchClient search;

    @GET
    @Path("/products")
    public Uni<Response> products() {
        return Relay.relay(catalog.products());
    }

    /** Product from the catalog, price from pricing and stock from inventory, fetched at the same time. */
    @GET
    @Path("/products/{id}")
    public Uni<ProductDetails> product(@PathParam("id") String id, @HeaderParam("X-Request-Id") String requestId) {
        return Uni.combine().all().unis(catalog.product(id), pricing.price(id, requestId), inventory.stock(id))
                .asTuple()
                .map(details -> new ProductDetails(details.getItem1(), details.getItem2().price(),
                        details.getItem2().lowestLast30Days(), details.getItem3().available(),
                        details.getItem2().priceHistory()))
                .onFailure(ProductResource::isNotFound)
                .transform(failure -> new NotFoundException("Unknown product " + id));
    }

    @GET
    @Path("/search")
    public Uni<Response> search(@QueryParam("q") String query) {
        return Relay.relay(search.search(query));
    }

    private static boolean isNotFound(Throwable failure) {
        return failure instanceof WebApplicationException e && e.getResponse().getStatus() == 404;
    }
}
