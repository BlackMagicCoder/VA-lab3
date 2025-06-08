package de.berlin.htw;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;


import jakarta.inject.Inject;

@QuarkusTest
class BasketResourceTest {

    @Inject
    protected RedisDataSource redisDS;
    
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
            .header("Location", "http://localhost:8081/hierFehltNoEtwas");
    }


    // Tests Aufgabe 2 - 5 Stück
    // Diese Tests prüfen die Validierung der Eingabedaten beim Hinzufügen von Artikeln zum Warenkorb
    @Test
    void testAddItem_missingProductName_shouldReturn400() {
        String json = """
        {
          "productName": "",
          "productId": "1-2-3-4-5-6",
          "count": 2,
          "price": 20.0
        }
        """;

        given()
                .log().all()
                .header("X-User-Id", "3")
                .contentType(ContentType.JSON)
                .body(json)
                .when()
                .post("/basket/1-2-3-4-5-6")
                .then()
                .log().all()
                .statusCode(400)
                .body(containsString("Produktname darf nicht null sein"));
    }

    @Test
    void testAddItem_invalidProductIdFormat_shouldReturn400() {
        String json = """
        {
          "productName": "Tasse",
          "productId": "abc-123",
          "count": 1,
          "price": 15.0
        }
        """;

        given()
                .log().all()
                .header("X-User-Id", "1")
                .contentType(ContentType.JSON)
                .body(json)
                .when()
                .post("/basket/abc-123")
                .then()
                .log().all()
                .statusCode(400)
                .body(containsString("Produktnummer muss aus 6 Zahlen bestehen"));
    }

    @Test
    void testAddItem_countTooLow_shouldReturn400() {
        String json = """
        {
          "productName": "Taschenlampe",
          "productId": "1-2-3-4-5-6",
          "count": 0,
          "price": 25.0
        }
        """;

        given()
                .log().all()
                .header("X-User-Id", "7")
                .contentType(ContentType.JSON)
                .body(json)
                .when()
                .post("/basket/1-2-3-4-5-6")
                .then()
                .log().all()
                .statusCode(400)
                .body(containsString("Anzahl muss mindestens 1 sein"));
    }

    @Test
    void testAddItem_priceTooLow_shouldReturn400() {
        String json = """
        {
          "productName": "Monitor",
          "productId": "1-2-3-4-5-6",
          "count": 1,
          "price": 5.0
        }
        """;

        given()
                .log().all()
                .header("X-User-Id", "8")
                .contentType(ContentType.JSON)
                .body(json)
                .when()
                .post("/basket/1-2-3-4-5-6")
                .then()
                .log().all()
                .statusCode(400)
                .body(containsString("Preis muss mindestens 10 Euro betragen"));
    }

    @Test
    void testAddItem_priceTooHigh_shouldReturn400() {
        String json = """
        {
          "productName": "Expensive Monitor",
          "productId": "1-2-3-4-5-6",
          "count": 1,
          "price": 150.0
        }
        """;

        given()
                .log().all()
                .header("X-User-Id", "9")
                .contentType(ContentType.JSON)
                .body(json)
                .when()
                .post("/basket/1-2-3-4-5-6")
                .then()
                .log().all()
                .statusCode(400)
                .body(containsString("Preis darf maximal 100 Euro betragen"));
    }

    @Test
    void testAddItem_priceMissing_shouldReturn400() {
        String json = """
        {
          "productName": "Kaffeetasse",
          "productId": "1-2-3-4-5-6",
          "count": 1,
          "price": null
        }
        """;

        given()
                .log().all()
                .header("X-User-Id", "10")
                .contentType(ContentType.JSON)
                .body(json)
                .when()
                .post("/basket/1-2-3-4-5-6")
                .then()
                .log().all()
                .statusCode(400)
                .body(containsString("Preis darf nicht null sein"));
    }
}