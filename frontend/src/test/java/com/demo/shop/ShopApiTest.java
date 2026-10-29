package com.demo.shop;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/** End-to-end tests of the shop and demo control REST API. Also run against the packaged app by ShopApiIT. */
@QuarkusTest
class ShopApiTest {

    @AfterEach
    void resetDemo() {
        given().post("/api/demo/reset").then().statusCode(200);
    }

    @Test
    void listsTheCatalog() {
        given().get("/api/products")
                .then().statusCode(200)
                .body("$", hasSize(8))
                .body("id", hasItem("red-fedora"));
    }

    @Test
    void showsProductDetailsWithPriceHistory() {
        given().get("/api/products/red-fedora")
                .then().statusCode(200)
                .body("product.name", is("Classic Red Fedora"))
                .body("price", is(49.0f))
                .body("priceHistory", hasSize(90))
                .body("inStock", greaterThan(0));
    }

    @Test
    void unknownProductIsNotFound() {
        given().get("/api/products/unicorn").then().statusCode(404);
    }

    @Test
    void searchToleratesTypos() {
        given().queryParam("q", "backpak").get("/api/search")
                .then().statusCode(200)
                .body("count", is(1))
                .body("products[0].id", is("laptop-backpack"));
    }

    @Test
    void placesAnOrder() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"items":[{"productId":"red-fedora","quantity":2},{"productId":"sticker-pack","quantity":1}]}""")
                .post("/api/checkout")
                .then().statusCode(200)
                .body("orderId", startsWith("SW-"))
                .body("total", is(106.0f))
                .body("items", hasSize(2));
    }

    @Test
    void rejectsInvalidOrders() {
        given().contentType(ContentType.JSON).body("{\"items\":[]}").post("/api/checkout")
                .then().statusCode(400);
        given().contentType(ContentType.JSON).body("{\"items\":[{\"productId\":\"unicorn\",\"quantity\":1}]}")
                .post("/api/checkout")
                .then().statusCode(400);
        given().contentType(ContentType.JSON).body("{\"items\":[{\"productId\":\"red-fedora\",\"quantity\":99}]}")
                .post("/api/checkout")
                .then().statusCode(400);
    }

    @Test
    void switchesScenariosAndResetsToDefaults() {
        given().get("/api/demo/settings")
                .then().statusCode(200)
                .body("scenarios", hasSize(3))
                .body("scenarios.find { it.id == 'search' }.problemEnabled", is(false));

        given().contentType(ContentType.JSON).body("{\"problemEnabled\":true,\"load\":\"HIGH\"}")
                .put("/api/demo/scenarios/search")
                .then().statusCode(200)
                .body("problemEnabled", is(true))
                .body("load", is("HIGH"));

        given().contentType(ContentType.JSON).body("{\"enabled\":true}").put("/api/demo/traffic")
                .then().statusCode(200).body("trafficEnabled", is(true));

        given().post("/api/demo/reset")
                .then().statusCode(200)
                .body("trafficEnabled", is(false))
                .body("scenarios.find { it.id == 'search' }.problemEnabled", is(false))
                .body("scenarios.find { it.id == 'search' }.load", is("MEDIUM"));
    }

    @Test
    void naiveSearchFindsTheSameProducts() {
        given().contentType(ContentType.JSON).body("{\"problemEnabled\":true}").put("/api/demo/scenarios/search")
                .then().statusCode(200);
        given().queryParam("q", "hoody").get("/api/search")
                .then().statusCode(200)
                .body("products[0].id", is("charcoal-zip-hoodie"));
    }

    @Test
    void leakingPriceCacheGrowsAndIsClearedByTheFix() {
        given().contentType(ContentType.JSON).body("{\"problemEnabled\":true}").put("/api/demo/scenarios/pricing")
                .then().statusCode(200);
        for (int i = 0; i < 20; i++) {
            given().get("/api/products/red-water-bottle").then().statusCode(200);
        }
        given().get("/api/demo/stats").then().statusCode(200).body("priceCache.entries", is(20));

        given().contentType(ContentType.JSON).body("{\"problemEnabled\":false}").put("/api/demo/scenarios/pricing")
                .then().statusCode(200);
        given().get("/api/demo/stats").then().statusCode(200).body("priceCache.entries", is(0));
    }

    @Test
    void naiveCheckoutStillPlacesOrders() {
        given().contentType(ContentType.JSON).body("{\"problemEnabled\":true}").put("/api/demo/scenarios/checkout")
                .then().statusCode(200);
        given().contentType(ContentType.JSON).body("{\"items\":[{\"productId\":\"red-t-shirt\",\"quantity\":1}]}")
                .post("/api/checkout")
                .then().statusCode(200)
                .body("total", is(24.0f));
    }

    @Test
    void reportsStatistics() {
        given().get("/api/products/red-fedora").then().statusCode(200);
        given().get("/api/demo/stats")
                .then().statusCode(200)
                .body("jvm.heapMaxMb", greaterThan(0))
                .body("requests.pricing.total", greaterThan(0));
    }

    @Test
    void servesTheNaiveAndFixedSourceCode() {
        given().get("/api/demo/source/checkout")
                .then().statusCode(200)
                .body("problem.className", is("SynchronizedInventory"))
                .body("problem.code", containsString("public synchronized Reservation reserve"))
                .body("fix.className", is("PerProductLockInventory"));
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
