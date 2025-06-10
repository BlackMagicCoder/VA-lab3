package de.berlin.htw;
import de.berlin.htw.boundary.dto.Item;
import jakarta.validation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ItemTest {

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
        item.setCount(3);
        item.setPrice(50.0f);
        return item;
    }

    @Test
    void validItem_shouldHaveNoViolations() {
        Item item = validItem();
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertTrue(violations.isEmpty(), "Ein gültiges Item sollte keine Validierungsfehler haben.");
    }

    @Test
    void productNameNull_shouldFail() {
        Item item = validItem();
        item.setProductName(null);
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("productName")));
    }

    @Test
    void productNameTooLong_shouldFail() {
        Item item = validItem();
        item.setProductName("a".repeat(256));
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertFalse(violations.isEmpty());
    }

    @Test
    void productIdWrongFormat_shouldFail() {
        Item item = validItem();
        item.setProductId("123-456");  // Falsches Format
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertFalse(violations.isEmpty());
    }

    @Test
    void countNull_shouldFail() {
        Item item = validItem();
        item.setCount(null);
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertFalse(violations.isEmpty());
    }

    @Test
    void countTooLow_shouldFail() {
        Item item = validItem();
        item.setCount(0);
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertFalse(violations.isEmpty());
    }

    @Test
    void priceNull_shouldFail() {
        Item item = validItem();
        item.setPrice(null);
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertFalse(violations.isEmpty());
    }

    @Test
    void priceTooLow_shouldFail() {
        Item item = validItem();
        item.setPrice(5.0f);  // Unter dem Minimum
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertFalse(violations.isEmpty());
    }

    @Test
    void priceTooHigh_shouldFail() {
        Item item = validItem();
        item.setPrice(150.0f);  // Über dem Maximum
        Set<ConstraintViolation<Item>> violations = validator.validate(item);
        assertFalse(violations.isEmpty());
    }
}
