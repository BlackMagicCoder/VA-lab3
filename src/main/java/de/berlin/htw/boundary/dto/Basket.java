package de.berlin.htw.boundary.dto;

/**
 * Data Transfer Object (DTO) für den Warenkorb.
 * Erbt von {@link Order} und fügt das verbleibende Guthaben des Benutzers hinzu.
 * Die Validierung der Artikelanzahl (max. 10) wird von der Superklasse {@link Order} geerbt.
 */

/**
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
public class Basket extends Order {

    /**
     * Das verbleibende Guthaben des Benutzers nach Berücksichtigung der Artikel im Warenkorb.
     * Dieser Wert wird typischerweise serverseitig berechnet und dient zur Information des Nutzers.
     */

    private Float remainingBalance;

    // Getter und Setter


	/**
	 * Gibt das verbleibende Guthaben zurück.
	 * @return Das verbleibende Guthaben als Float.
	 */
	public Float getRemainingBalance() {
		return remainingBalance;
	}

	/**
	 * Setzt das verbleibende Guthaben.
	 * @param remainingBalance Das zu setzende verbleibende Guthaben.
	 */
	public void setRemainingBalance(Float remainingBalance) {
		this.remainingBalance = remainingBalance;
	}

}
