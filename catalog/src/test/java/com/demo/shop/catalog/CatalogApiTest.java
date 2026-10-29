package com.demo.shop.catalog;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class CatalogApiTest {

    @Test
    void listsTheCatalog() {
        given().get("/api/products")
                .then().statusCode(200)
                .body("$", hasSize(8))
                .body("id", hasItem("red-fedora"));
    }

    @Test
    void showsOneProduct() {
        given().get("/api/products/red-fedora")
                .then().statusCode(200)
                .body("name", is("Classic Red Fedora"))
                .body("price", is(49.0f))
                .body("image", is("/img/products/red-fedora.webp"))
                .body("tags", hasItem("fedora"));
    }

    @Test
    void unknownProductIsNotFound() {
        given().get("/api/products/unicorn").then().statusCode(404);
    }
}
