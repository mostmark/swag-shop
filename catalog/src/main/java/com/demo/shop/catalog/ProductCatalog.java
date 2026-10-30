package com.demo.shop.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The shop's products, read from products.json. The other services fetch them from here when they start. */
@ApplicationScoped
public class ProductCatalog {

    static final String PRODUCTS_FILE = "products.json";

    private final List<Product> products;

    @Inject
    public ProductCatalog(ObjectMapper mapper) {
        try (InputStream in = ProductCatalog.class.getClassLoader().getResourceAsStream(PRODUCTS_FILE)) {
            if (in == null) {
                throw new IllegalStateException(PRODUCTS_FILE + " is missing");
            }
            products = List.copyOf(mapper.readValue(in, new TypeReference<List<Product>>() {
            }));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + PRODUCTS_FILE, e);
        }
    }

    public List<Product> all() {
        return products;
    }

    public Optional<Product> findById(String id) {
        return products.stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
