package com.demo.shop.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.runtime.Startup;

/**
 * The shop's products, fetched from the catalog service when this service starts. The catalog may still be
 * starting too, so the fetch is retried for a while before the service gives up.
 *
 * <p>
 * With catalog.bundled=true (the standalone profile) the copy of products.json packaged with this service is
 * used instead, so the service runs on its own.
 */
@Startup
@ApplicationScoped
public class ProductCatalog {

    private static final Logger LOG = Logger.getLogger(ProductCatalog.class);
    private static final String PRODUCTS_FILE = "products.json";
    private static final TypeReference<List<Product>> PRODUCT_LIST = new TypeReference<>() {
    };
    static final Duration RETRY_INTERVAL = Duration.ofSeconds(2);
    static final Duration GIVE_UP_AFTER = Duration.ofMinutes(2);

    private List<Product> products;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "catalog.url")
    URI catalogUrl;

    @ConfigProperty(name = "catalog.bundled")
    boolean bundled;

    /** A catalog with the bundled products, for tests that create the services by hand. */
    public static ProductCatalog bundled() {
        ProductCatalog catalog = new ProductCatalog();
        catalog.products = readBundled(new ObjectMapper());
        return catalog;
    }

    @PostConstruct
    void load() {
        products = bundled ? readBundled(mapper) : fetch();
        LOG.infof("Loaded %d products from %s", products.size(), bundled ? "the bundled " + PRODUCTS_FILE
                : catalogUrl);
    }

    public List<Product> all() {
        return products;
    }

    public Optional<Product> findById(String id) {
        return products.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    private List<Product> fetch() {
        URI uri = catalogUrl.resolve("/api/products");
        long deadline = System.nanoTime() + GIVE_UP_AFTER.toNanos();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(RETRY_INTERVAL).build()) {
            while (true) {
                try {
                    HttpResponse<String> response = client.send(
                            HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).build(),
                            HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        return List.copyOf(mapper.readValue(response.body(), PRODUCT_LIST));
                    }
                    LOG.warnf("The catalog at %s answered %d, retrying", uri, response.statusCode());
                } catch (IOException e) {
                    LOG.infof("Waiting for the catalog at %s (%s)", uri, e.getClass().getSimpleName());
                }
                if (System.nanoTime() - deadline > 0) {
                    throw new IllegalStateException("The catalog at " + uri + " did not answer within "
                            + GIVE_UP_AFTER.toSeconds() + " s");
                }
                Thread.sleep(RETRY_INTERVAL);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching the products", e);
        }
    }

    private static List<Product> readBundled(ObjectMapper mapper) {
        try (InputStream in = ProductCatalog.class.getClassLoader().getResourceAsStream(PRODUCTS_FILE)) {
            if (in == null) {
                throw new IllegalStateException(PRODUCTS_FILE + " is missing");
            }
            return List.copyOf(mapper.readValue(in, PRODUCT_LIST));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + PRODUCTS_FILE, e);
        }
    }
}
