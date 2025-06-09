package de.berlin.htw.boundary.dto;

/**
 * Data Transfer Object (DTO) für eine Bestellung.
 * Enthält eine Liste von Artikeln und die Gesamtsumme der Bestellung.
 * Die Liste der Artikel ist auf maximal 10 Einträge beschränkt und jeder Artikel wird validiert.
 */

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
public class Order {

    /**
     * Die Liste der Artikel in der Bestellung oder im Warenkorb.
     * Darf maximal 10 Artikel enthalten. Jeder Artikel in der Liste wird ebenfalls validiert (@Valid).
     */

    @Valid
    @Size(max = 10, message = "Der Warenkorb darf nicht mehr als 10 Artikel enthalten")
    private List<Item> items;

    /**
     * Die Gesamtsumme der Bestellung.
     * Dieser Wert wird typischerweise serverseitig berechnet.
     */
    
    private Float total;

    // Getter und Setter

    
    /**
     * Gibt die Liste der Artikel zurück.
     * @return Eine Liste von {@link Item} Objekten.
     */
    public List<Item> getItems() {
        return items;
    }

    /**
     * Setzt die Liste der Artikel.
     * @param items Eine Liste von {@link Item} Objekten.
     */
    public void setItems(List<Item> items) {
        this.items = items;
    }

    /**
     * Gibt die Gesamtsumme der Bestellung zurück.
     * @return Die Gesamtsumme als Float.
     */
    public Float getTotal() {
        return total;
    }

    /**
     * Setzt die Gesamtsumme der Bestellung.
     * @param total Die Gesamtsumme als Float.
     */
    public void setTotal(Float total) {
        this.total = total;
    }

}
