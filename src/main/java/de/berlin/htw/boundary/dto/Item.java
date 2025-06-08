package de.berlin.htw.boundary.dto;

import jakarta.validation.constraints.*;

/**
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
public class Item {

    @NotBlank(message = "Produktname darf nicht null sein")
    @Size(max = 255, message = "Produktname darf nicht länger als 255 Zeichen sein")
    private String productName;
    
    @NotBlank(message = "Produktnummer darf nicht null sein")
    @Pattern(regexp = "[0-9]-[0-9]-[0-9]-[0-9]-[0-9]-[0-9]", message = "Produktnummer muss aus 6 Zahlen bestehen, die durch Bindestriche getrennt sind")
    private String productId;
    
    @NotEmpty(message = "Anzahl darf nicht null sein")
    @Min(value = 1, message = "Anzahl muss mindestens 1 sein")
    private Integer count;
    
    @NotEmpty(message = "Preis darf nicht null sein")
    @Min(value = 10, message = "Preis muss mindestens 10 Euro betragen")
    @Max(value = 100, message = "Preis darf maximal 100 Euro betragen")
    private Float price;

    public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public String getProductId() {
		return productId;
	}

	public void setProductId(String productId) {
		this.productId = productId;
	}

	public Integer getCount() {
        return count;
    }

    public void setCount(final Integer count) {
        this.count = count;
    }
    
	public Float getPrice() {
		return price;
	}

	public void setPrice(Float price) {
		this.price = price;
	}

}
