package com.demo.shop.catalog;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/api/products")
public class ProductResource {

    @Inject
    ProductCatalog catalog;

    @GET
    public List<Product> products() {
        return catalog.all();
    }

    @GET
    @Path("/{id}")
    public Product product(@PathParam("id") String id) {
        return catalog.findById(id).orElseThrow(() -> new NotFoundException("Unknown product " + id));
    }
}
