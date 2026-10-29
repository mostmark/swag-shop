package com.demo.shop.pricing;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/** Tests of the price and demo REST API, with the bundled products (the test profile). */
@QuarkusTest
class PricingApiTest {

    @AfterEach
    void resetDemo() {
        given().post("/api/demo/reset").then().statusCode(200);
    }

    @Test
    void quotesThePriceWithPriceHistory() {
        given().get("/api/prices/red-fedora")
                .then().statusCode(200)
                .body("productId", is("red-fedora"))
                .body("price", is(49.0f))
                .body("lowestLast30Days", greaterThan(0f))
                .body("priceHistory", hasSize(PriceResource.CHART_DAYS));
    }

    @Test
    void unknownProductIsNotFound() {
        given().get("/api/prices/unicorn").then().statusCode(404);
    }

    @Test
    void leakingPriceCacheGrowsAndIsClearedByTheFix() {
        given().contentType(ContentType.JSON).body("{\"problemEnabled\":true}").put("/api/demo/scenario")
                .then().statusCode(200).body("id", is("pricing")).body("problemEnabled", is(true));
        for (int i = 0; i < 20; i++) {
            given().get("/api/prices/red-water-bottle").then().statusCode(200);
        }
        given().get("/api/demo/leak").then().statusCode(200)
                .body("entries", is(20))
                .body("maxEntries", greaterThan(20));

        given().contentType(ContentType.JSON).body("{\"problemEnabled\":false}").put("/api/demo/scenario")
                .then().statusCode(200);
        given().get("/api/demo/leak").then().statusCode(200).body("entries", is(0));
    }

    @Test
    void theFixedCacheKeepsOneQuotePerProduct() {
        for (int i = 0; i < 5; i++) {
            given().header("X-Request-Id", "request-" + i).get("/api/prices/sticker-pack").then().statusCode(200);
        }
        given().get("/api/demo/leak").then().statusCode(200).body("entries", is(0));
    }

    @Test
    void servesTheNaiveAndFixedSourceCode() {
        given().get("/api/demo/source")
                .then().statusCode(200)
                .body("problem.className", is("RequestQuoteCache"))
                .body("problem.path", is("pricing/src/main/java/com/demo/shop/pricing/RequestQuoteCache.java"))
                .body("fix.className", is("ProductQuoteCache"));
    }
}
