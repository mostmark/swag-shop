package com.demo.shop.search;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/** Tests of the search and demo REST API, with the bundled products (the test profile). */
@QuarkusTest
class SearchApiTest {

    @AfterEach
    void resetDemo() {
        given().post("/api/demo/reset").then().statusCode(200);
    }

    @Test
    void searchToleratesTypos() {
        given().queryParam("q", "backpak").get("/api/search")
                .then().statusCode(200)
                .body("query", is("backpak"))
                .body("count", is(1))
                .body("products[0].id", is("laptop-backpack"));
    }

    @Test
    void emptySearchReturnsEveryProduct() {
        given().get("/api/search").then().statusCode(200).body("count", is(8));
    }

    @Test
    void switchesTheProblemAndResetsToTheDefault() {
        given().get("/api/demo/scenario")
                .then().statusCode(200)
                .body("id", is("search"))
                .body("problemType", is("CPU hotspot"))
                .body("problemEnabled", is(false))
                .body("defaultProblemEnabled", is(false));

        given().contentType(ContentType.JSON).body("{\"problemEnabled\":true,\"changedBy\":\"test\"}")
                .put("/api/demo/scenario")
                .then().statusCode(200).body("problemEnabled", is(true));
        given().queryParam("q", "hoody").get("/api/search")
                .then().statusCode(200)
                .body("products[0].id", is("charcoal-zip-hoodie"));

        given().post("/api/demo/reset").then().statusCode(200).body("problemEnabled", is(false));
    }

    @Test
    void servesTheNaiveAndFixedSourceCode() {
        given().get("/api/demo/source")
                .then().statusCode(200)
                .body("scenario", is("search"))
                .body("problem.className", is("RecursiveEditDistance"))
                .body("problem.path", is("search/src/main/java/com/demo/shop/search/RecursiveEditDistance.java"))
                .body("problem.code", containsString("return Math.min(Math.min(deletion, insertion), substitution);"))
                .body("fix.className", is("IterativeEditDistance"));
    }

    @Test
    void reportsJvmStatistics() {
        given().get("/api/demo/jvm")
                .then().statusCode(200)
                .body("service", is("swag-shop-search"))
                .body("heapMaxMb", greaterThan(0))
                .body("liveThreads", greaterThan(0));
    }
}
