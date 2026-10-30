package com.demo.shop.inventory;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/** Tests of the inventory and demo REST API, with the bundled products (the test profile). */
@QuarkusTest
class InventoryApiTest {

    @AfterEach
    void resetDemo() {
        given().post("/api/demo/reset").then().statusCode(200);
    }

    @Test
    void reservesStock() {
        int before = given().get("/api/inventory/red-fedora").then().statusCode(200).extract().path("available");
        given().contentType(ContentType.JSON).body("{\"productId\":\"red-fedora\",\"quantity\":2}")
                .post("/api/inventory/reservations")
                .then().statusCode(200)
                .body("productId", is("red-fedora"))
                .body("remaining", is(before - 2));
    }

    @Test
    void rejectsInvalidReservations() {
        given().contentType(ContentType.JSON).body("{\"productId\":\"unicorn\",\"quantity\":1}")
                .post("/api/inventory/reservations").then().statusCode(400);
        given().contentType(ContentType.JSON).body("{\"productId\":\"red-fedora\",\"quantity\":0}")
                .post("/api/inventory/reservations").then().statusCode(400);
        given().get("/api/inventory/unicorn").then().statusCode(404);
    }

    @Test
    void naiveInventoryStillReserves() {
        given().contentType(ContentType.JSON).body("{\"problemEnabled\":true}").put("/api/demo/scenario")
                .then().statusCode(200).body("id", is("checkout")).body("problemEnabled", is(true));
        given().contentType(ContentType.JSON).body("{\"productId\":\"red-t-shirt\",\"quantity\":1}")
                .post("/api/inventory/reservations")
                .then().statusCode(200).body("productId", is("red-t-shirt"));
    }

    @Test
    void servesTheNaiveAndFixedSourceCode() {
        given().get("/api/demo/source")
                .then().statusCode(200)
                .body("problem.className", is("SynchronizedInventory"))
                .body("problem.code", containsString("public synchronized Reservation reserve"))
                .body("fix.className", is("PerProductLockInventory"));
    }
}
