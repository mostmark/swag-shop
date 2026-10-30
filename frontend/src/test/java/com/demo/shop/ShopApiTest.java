package com.demo.shop;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/** Tests of what the frontend does with the services' answers, against fake services. */
@QuarkusTest
@QuarkusTestResource(FakeServices.class)
class ShopApiTest {

    @AfterEach
    void resetFakes() {
        FakeServices.reset();
    }

    @Test
    void listsTheCatalog() {
        given().get("/api/products")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", is("red-fedora"));
    }

    @Test
    void combinesProductPriceAndStock() {
        given().header("X-Request-Id", "request-1").get("/api/products/red-fedora")
                .then().statusCode(200)
                .body("product.name", is("Classic Red Fedora"))
                .body("price", is(49.0f))
                .body("lowestLast30Days", is(39.2f))
                .body("inStock", is(497))
                .body("priceHistory", hasSize(2));
        assertEquals("request-1", FakeServices.LAST_REQUEST.get("pricing X-Request-Id"));
    }

    @Test
    void unknownProductIsNotFound() {
        given().get("/api/products/unicorn").then().statusCode(404);
    }

    @Test
    void passesSearchesOn() {
        given().queryParam("q", "backpak").get("/api/search")
                .then().statusCode(200)
                .body("query", is("backpak"))
                .body("count", is(1));
    }

    @Test
    void passesOrdersOnWithTheirErrors() {
        String order = "{\"items\":[{\"productId\":\"red-fedora\",\"quantity\":1}]}";
        given().contentType(ContentType.JSON).body(order).post("/api/checkout")
                .then().statusCode(200).body("orderId", is("SW-12345678"));
        assertEquals(order, FakeServices.LAST_REQUEST.get("order POST /api/checkout"));

        given().contentType(ContentType.JSON).body("{\"items\":[{\"productId\":\"unicorn\",\"quantity\":1}]}")
                .post("/api/checkout")
                .then().statusCode(400).body(is("Unknown product unicorn"));
    }

    @Test
    void combinesTheScenariosWithTheirLoad() {
        given().get("/api/demo/settings")
                .then().statusCode(200)
                .body("trafficEnabled", is(false))
                .body("scenarios", hasSize(3))
                .body("scenarios.id", equalTo(java.util.List.of("search", "pricing", "checkout")))
                .body("scenarios[0].feature", is("Feature search"))
                .body("scenarios[0].load", is("MEDIUM"))
                .body("scenarios[0].requestsPerSecond.HIGH", is(30))
                .body("scenarios[0].jvmService", is("search"))
                .body("scenarios[2].jvmService", is("order"));
    }

    @Test
    void passesScenarioChangesOnToTheRightServices() {
        given().contentType(ContentType.JSON).body("{\"problemEnabled\":true,\"load\":\"HIGH\"}")
                .put("/api/demo/scenarios/checkout")
                .then().statusCode(200)
                .body("id", is("checkout"))
                .body("problemEnabled", is(true))
                .body("load", is("HIGH"));
        assertEquals(true, FakeServices.PROBLEM_ENABLED.get("checkout"));
        assertEquals("{\"problemEnabled\":true,\"changedBy\":\"demo control page\"}",
                FakeServices.LAST_REQUEST.get("inventory PUT /api/demo/scenario"));

        given().post("/api/demo/reset")
                .then().statusCode(200)
                .body("scenarios.find { it.id == 'checkout' }.problemEnabled", is(false))
                .body("scenarios.find { it.id == 'checkout' }.load", is("MEDIUM"));
        given().contentType(ContentType.JSON).body("{\"problemEnabled\":true}").put("/api/demo/scenarios/unknown")
                .then().statusCode(404);
    }

    @Test
    void switchesTheShoppers() {
        given().contentType(ContentType.JSON).body("{\"enabled\":true}").put("/api/demo/traffic")
                .then().statusCode(200).body("trafficEnabled", is(true));
    }

    @Test
    void collectsTheStatisticsOfEveryService() {
        given().get("/api/demo/stats")
                .then().statusCode(200)
                .body("trafficEnabled", is(true))
                .body("requests.search.total", is(60))
                .body("jvm.search.service", is("swag-shop-search"))
                .body("jvm.pricing.service", is("swag-shop-pricing"))
                .body("jvm.checkout.service", is("swag-shop-order"))
                .body("priceCache.entries", is(20))
                .body("ordersPlaced", is(42));
    }

    @Test
    void statisticsLeaveOutAServiceThatIsDown() {
        FakeServices.BROKEN.put("pricing", true);
        given().get("/api/demo/stats")
                .then().statusCode(200)
                .body("jvm.pricing", nullValue())
                .body("priceCache", nullValue())
                .body("jvm.search.service", is("swag-shop-search"))
                .body("ordersPlaced", is(42));
    }

    @Test
    void servesTheSourceCodeOfTheScenarioService() {
        given().get("/api/demo/source/checkout")
                .then().statusCode(200)
                .body("scenario", is("checkout"))
                .body("problem.className", is("Naive"));
        given().get("/api/demo/source/unknown").then().statusCode(404);
    }

    @Test
    void servesTheWebPages() {
        for (String page : new String[] { "/", "/product.html", "/cart.html", "/demo.html" }) {
            given().get(page).then().statusCode(200).body(containsString("Swag Shop"));
        }
        given().get("/js/importmap.js").then().statusCode(200);
        given().get("/_static/at/rhds/elements/5.0.0/elements/rh-switch/rh-switch.js")
                .then().statusCode(200);
        given().get("/img/products/red-fedora.webp").then().statusCode(200)
                .header("Cache-Control", equalTo("max-age=86400"));
    }
}
