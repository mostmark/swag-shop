package com.demo.shop.order;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/** Tests of the checkout and demo REST API, with the bundled products and a fake inventory service. */
@QuarkusTest
@QuarkusTestResource(FakeInventory.class)
class OrderApiTest {

    @Test
    void placesAnOrderAndReservesEveryLine() {
        int before = FakeInventory.RESERVATIONS.get();
        given().contentType(ContentType.JSON)
                .body("""
                        {"items":[{"productId":"red-fedora","quantity":2},{"productId":"sticker-pack","quantity":1}]}""")
                .post("/api/checkout")
                .then().statusCode(200)
                .body("orderId", startsWith("SW-"))
                .body("total", is(106.0f))
                .body("items", hasSize(2));
        assertEquals(before + 2, FakeInventory.RESERVATIONS.get());
        given().get("/api/demo/orders").then().statusCode(200).body("ordersPlaced", greaterThan(0));
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
    void reportsJvmStatistics() {
        given().get("/api/demo/jvm").then().statusCode(200).body("service", is("swag-shop-order"));
    }
}
