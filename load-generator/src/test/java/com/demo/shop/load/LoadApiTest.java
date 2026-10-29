package com.demo.shop.load;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/** Tests of the load generator, sending to a fake shop. */
@QuarkusTest
@QuarkusTestResource(FakeShop.class)
class LoadApiTest {

    @AfterEach
    void resetLoad() {
        given().post("/api/load/reset").then().statusCode(200);
    }

    @Test
    void changesTheLoadAndResetsToDefaults() {
        given().get("/api/load")
                .then().statusCode(200)
                .body("trafficEnabled", is(false))
                .body("scenarios", hasSize(3))
                .body("scenarios.find { it.id == 'search' }.load", is("MEDIUM"))
                .body("scenarios.find { it.id == 'search' }.requestsPerSecond.HIGH", is(30));

        given().contentType(ContentType.JSON).body("{\"load\":\"HIGH\"}").put("/api/load/scenarios/checkout")
                .then().statusCode(200).body("load", is("HIGH")).body("defaultLoad", is("MEDIUM"));
        given().contentType(ContentType.JSON).body("{\"load\":\"HIGH\"}").put("/api/load/scenarios/unknown")
                .then().statusCode(404);

        given().post("/api/load/reset")
                .then().statusCode(200)
                .body("scenarios.find { it.id == 'checkout' }.load", is("MEDIUM"));
    }

    @Test
    void shoppersUseEveryFeatureAndTheResponseTimesAreMeasured() throws Exception {
        given().contentType(ContentType.JSON).body("{\"enabled\":true}").put("/api/load/traffic")
                .then().statusCode(200).body("trafficEnabled", is(true));
        Thread.sleep(1500);
        given().contentType(ContentType.JSON).body("{\"enabled\":false}").put("/api/load/traffic")
                .then().statusCode(200);

        assertTrue(FakeShop.requests("GET /api/search") > 0);
        assertTrue(FakeShop.requests("GET /api/products/") > 0);
        assertTrue(FakeShop.requests("POST /api/checkout") > 0);
        given().get("/api/load/stats")
                .then().statusCode(200)
                .body("requests.search.total", greaterThan(0))
                .body("requests.pricing.requestsPerSecond", greaterThan(0f))
                .body("requests.checkout.failed", is(0));
    }
}
