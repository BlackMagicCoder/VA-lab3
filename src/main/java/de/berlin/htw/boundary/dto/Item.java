package de.berlin.htw.boundary.dto;

/**
 * Data Transfer Object (DTO) für einen Artikel.
 * Repräsentiert einen Artikel mit Produktnamen, Produkt-ID, Anzahl und Preis.
 * Enthält Bean Validation Annotationen zur Sicherstellung der Datenintegrität.
 */

import jakarta.validation.constraints.*;

/**
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
public class Item {

    /**
     * Der Name des Produkts.
     * Darf nicht leer sein und maximal 255 Zeichen lang sein.
     */

    @NotBlank(message = "Produktname darf nicht null sein")
    @Size(max = 255, message = "Produktname darf nicht länger als 255 Zeichen sein")
    private String productName;

    /**
     * Die eindeutige ID des Produkts.
     * Muss dem Format 'X-X-X-X-X-X' entsprechen, wobei X eine Ziffer ist.
     * Darf nicht leer sein.
     */
    
    @NotBlank(message = "Produktnummer darf nicht null sein")
    @Pattern(regexp = "[0-9]-[0-9]-[0-9]-[0-9]-[0-9]-[0-9]", message = "Produktnummer muss aus 6 Zahlen bestehen, die durch Bindestriche getrennt sind")
    private String productId;

    /**
     * Die Anzahl dieses Artikels.
     * Muss mindestens 1 sein und darf nicht null sein.
     */
    
    @NotNull(message = "Anzahl darf nicht null sein")
    @Min(value = 1, message = "Anzahl muss mindestens 1 sein")
    private Integer count;

    /**
     * Der Preis des Artikels.
     * Muss zwischen 10 und 100 (einschließlich) liegen und darf nicht null sein.
     */
    
    @NotNull(message = "Preis darf nicht null sein")
    @Min(value = 10, message = "Preis muss mindestens 10 Euro betragen")
    @Max(value = 100, message = "Preis darf maximal 100 Euro betragen")
    private Float price;

    // Getter und Setter


    /**
     * Gibt den Produktnamen zurück.
     * @return Der Produktname.
     */
    public String getProductName() {
		return productName;
	}

	/**
	 * Setzt den Produktnamen.
	 * @param productName Der zu setzende Produktname.
	 */
	public void setProductName(String productName) {
		this.productName = productName;
	}

	/**
	 * Gibt die Produkt-ID zurück.
	 * @return Die Produkt-ID.
	 */
	public String getProductId() {
		return productId;
	}

	/**
	 * Setzt die Produkt-ID.
	 * @param productId Die zu setzende Produkt-ID.
	 */
	public void setProductId(String productId) {
		this.productId = productId;
	}

	/**
	 * Gibt die Anzahl des Artikels zurück.
	 * @return Die Anzahl.
	 */
	public Integer getCount() {
        return count;
    }

    /**
     * Setzt die Anzahl des Artikels.
     * @param count Die zu setzende Anzahl.
     */
    public void setCount(final Integer count) {
        this.count = count;
    }
    
	/**
	 * Gibt den Preis des Artikels zurück.
	 * @return Der Preis.
	 */
	public Float getPrice() {
		return price;
	}

	/**
	 * Setzt den Preis des Artikels.
	 * @param price Der zu setzende Preis.
	 */
	public void setPrice(Float price) {
		this.price = price;
	}

}
