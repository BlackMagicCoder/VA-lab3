package de.berlin.htw;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.*;
import jakarta.inject.Inject;

@QuarkusTest
class BasketResourceTest {

    @Inject
    protected RedisDataSource redisDS;

    // Your existing tests
    @Test
    void testGetBasket() {
        ValueCommands<String, Integer> countCommands = redisDS.value(Integer.class);

        given()
                .log().all()
                .when().header("X-User-Id", "2")
                .get("/basket")
                .then()
                .log().all()
                .statusCode(415);

        assertEquals(88, countCommands.get("TODO"));
    }

    @Test
    void testAddItem() {
        given()
                .log().all()
                .when().header("X-User-Id", "3")
                .contentType(ContentType.JSON)
                .post("/basket/anyID")
                .then()
                .log().all()
                .statusCode(501);
    }

    @Test
    void testCheckout() {
        given()
                .log().all()
                .when().header("X-User-Id", "4")
                .post("/basket")
                .then()
                .log().all()
                .statusCode(201)
                .header("Location", "http://localhost:8081/orders/test-order-id");
    }


    // New tests for additional functionality
    @Test
    void testClearBasket() {
        given()
                .log().all()
                .when().header("X-User-Id", "1")
                .delete("/basket")
                .then()
                .log().all()
                .statusCode(204);
    }

    @Test
    void testClearBasketSuccess() {
        // Test clearing basket - should return 204 No Content
        given()
                .log().all()
                .when().header("X-User-Id", "1")
                .delete("/basket")
                .then()
                .log().all()
                .statusCode(204);
    }

    @Test
    void testAddItemWithInvalidJson() {
        // Test adding item with invalid JSON format
        given()
                .log().all()
                .when().header("X-User-Id", "1")
                .contentType(ContentType.JSON)
                .body("{ invalid json }")
                .post("/basket/test-product")
                .then()
                .log().all()
                .statusCode(400);
    }

    @Test
    void testRemoveNonExistentItem() {
        // Test removing an item that doesn't exist in basket
        given()
                .log().all()
                .when().header("X-User-Id", "1")
                .delete("/basket/non-existent-product")
                .then()
                .log().all()
                .statusCode(404);
    }

    @Test
    void testInvalidUserAccess() {
        // Test with a user ID that doesn't exist in the database
        given()
                .log().all()
                .when().header("X-User-Id", "999")
                .get("/basket")
                .then()
                .log().all()
                .statusCode(401);
    }

    @Test
    void testUnauthorizedAccess() {
        // Test without X-User-Id header
        given()
                .log().all()
                .when()
                .get("/basket")
                .then()
                .log().all()
                .statusCode(401);
    }
}