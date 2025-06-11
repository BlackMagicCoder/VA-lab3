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

    @Inject
    protected de.berlin.htw.entity.dao.UserRepository userRepository;

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


    // NEW tests for additional functionality
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

    @Test
    void testNoUserIdHeader() {
        // Test accessing basket without X-User-Id header
        given()
                .log().all()
                .when()
                .get("/basket")
                .then()
                .log().all()
                .statusCode(401);
    }

    @Test
    @jakarta.transaction.Transactional
    void testAddMoreThan10UniqueItems_shouldFail() {
        final String userName = "max-item-user";

        // User anlegen, damit Guthaben existiert.
        de.berlin.htw.entity.dto.UserEntity user = userRepository.findByName(userName);
        if (user == null) {
            user = new de.berlin.htw.entity.dto.UserEntity();
            user.setName(userName);
            user.setBalance(1000.0f);
            userRepository.persistUser(user);
        } else {
            // Wenn der User schon existiert, Guthaben zurücksetzen, um Teststabilität zu gewährleisten
            user.setBalance(1000.0f);
            userRepository.updateUserBalance(user);
        }

        final Integer userId = user.getId();

        // Sicherstellen, dass der Warenkorb für diesen Test leer ist.
        given().header("X-User-Id", userId).delete("/basket").then().statusCode(anyOf(is(204), is(404)));

        // 10 verschiedene Artikel hinzufügen (sollte erfolgreich sein)
        for (int i = 0; i < 10; i++) {
            String productId = "1-2-3-4-5-" + i; // Erzeugt gültige IDs von ...-0 bis ...-9
            de.berlin.htw.boundary.dto.Item item = new de.berlin.htw.boundary.dto.Item();
            item.setProductId(productId);
            item.setProductName("Test Item " + i);
            item.setPrice(10.0f);
            item.setCount(1);
            given()
                .header("X-User-Id", userId)
                .contentType(ContentType.JSON)
                .body(item)
                .post("/basket/" + productId)
                .then()
                .statusCode(201);
        }

        // 11. Artikel hinzufügen (sollte fehlschlagen)
        String eleventhProductId = "1-2-3-4-6-0"; // Eine andere, aber gültige Produkt-ID
        de.berlin.htw.boundary.dto.Item eleventhItem = new de.berlin.htw.boundary.dto.Item();
        eleventhItem.setProductId(eleventhProductId);
        eleventhItem.setProductName("Test Item 11");
        eleventhItem.setPrice(10.0f);
        eleventhItem.setCount(1);
        given()
            .header("X-User-Id", userId)
            .contentType(ContentType.JSON)
            .body(eleventhItem)
            .post("/basket/" + eleventhProductId)
            .then()
            .statusCode(409); // Erwartet 409 Conflict, da der Warenkorb voll ist.
    }
}