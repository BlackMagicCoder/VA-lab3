package de.berlin.htw;
import de.berlin.htw.boundary.dto.Item;
import jakarta.validation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ItemTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Item validItem() {
        Item item = new Item();
        item.setProductName("Schokolade");
        item.setProductId("1-2-3-4-5-6");
        item.setPrice(50.0f);
        item.setCount(1); // Fehlende Initialisierung für @NotNull-Feld
        return item;
    }

    @Test
    void articleNameLengthValidation() {
        Item item = validItem();
        item.setProductName("a".repeat(256));
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertFalse(violations.isEmpty(), "256 Zeichen sollten ungültig sein");

        item.setProductName("a".repeat(255));
        violations = validator.validate(item);
        assertTrue(violations.isEmpty(), "255 Zeichen sollten gültig sein");
    }

    @Test
    void articleIdFormatValidation() {
        Item item = validItem();
        item.setProductId("1-2-3-4-5"); // Ungültiges Format
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertFalse(violations.isEmpty(), "Format 1-2-3-4-5 sollte ungültig sein");

        item.setProductId("1-2-3-4-5-6"); // Gültiges Format
        violations = validator.validate(item);
        assertTrue(violations.isEmpty(), "Format 1-2-3-4-5-6 sollte gültig sein");
    }

    @Test
    void priceTooLow() {
        Item item = validItem();
        item.setPrice(9.99f);
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertFalse(violations.isEmpty());
    }

    @Test
    void priceTooHigh() {
        Item item = validItem();
        item.setPrice(100.01f);
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertFalse(violations.isEmpty());
    }

    @Test
    void priceOnBoundary() {
        Item item = validItem();
        item.setPrice(10.00f);
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertTrue(violations.isEmpty(), "Preis von 10.00 sollte gültig sein");

        item.setPrice(100.00f);
        violations = validator.validate(item);
        assertTrue(violations.isEmpty(), "Preis von 100.00 sollte gültig sein");
    }
}
