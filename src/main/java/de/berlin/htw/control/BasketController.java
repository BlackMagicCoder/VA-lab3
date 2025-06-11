package de.berlin.htw.control;

/**
 * Controller für die Verwaltung von Warenkörben.
 * Nutzt Redis zur Speicherung der Warenkorbdaten und implementiert die Geschäftslogik
 * wie Guthabenprüfung, maximale Artikelanzahl und Warenkorb-Timeout.
 */

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.berlin.htw.boundary.dto.Basket;
import de.berlin.htw.boundary.dto.Item;
import de.berlin.htw.entity.dao.UserRepository;
import de.berlin.htw.entity.dto.UserEntity;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import jakarta.validation.Validator;
import java.math.BigDecimal;

/**
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
@Dependent
public class BasketController {

    private static final int MAX_ITEMS_IN_BASKET = 10;

    // Konstante für das Timeout des Warenkorbs (2 Minuten gemäß Aufgabe 3)
    private static final Duration BASKET_TIMEOUT = Duration.ofMinutes(2);

    @Inject
    protected RedisDataSource redisDS; // Redis-Datenquelle für den Zugriff auf Redis

    @Inject
    protected UserRepository userRepository; // Repository für den Zugriff auf Benutzerdaten (z.B. Guthaben)

    @Inject
    protected Validator validator; // Bean Validation Validator (hier weniger genutzt, da Annotationen dominieren)

    @Inject
    protected ObjectMapper objectMapper; // Für die Serialisierung/Deserialisierung von Item-Objekten zu/von JSON

    @Inject
    Logger logger; // Logger injiziert

    // Redis Commands für Hash-Operationen (jedes Item im Warenkorb ist ein Feld im Hash des Benutzers)
    protected HashCommands<String, String, String> hashCommands;

    // Redis Commands für Key-Operationen (z.B. zum Löschen des gesamten Warenkorbs oder Setzen des Timeouts)
    protected KeyCommands<String> keyCommands;

    /**
     * Initialisiert die Redis-Command-Objekte nach der Injektion der Abhängigkeiten.
     */
    @PostConstruct
    protected void init() {
        // Initialisiert HashCommands für <Key, Field, Value> wobei Key=basketKey, Field=productId, Value=itemAsJson
        hashCommands = redisDS.hash(String.class);
        // Initialisiert KeyCommands für Operationen auf dem Basket-Key selbst
        keyCommands = redisDS.key();
    }

    /**
     * Erstellt einen eindeutigen Redis-Schlüssel für den Warenkorb eines bestimmten Benutzers.
     * Format: "basket:userId"
     * @param userId Die ID des Benutzers.
     * @return Der generierte Redis-Schlüssel für den Warenkorb.
     */
    private String getBasketKey(String userId) {
        return "basket:" + userId;
    }

    /**
     * Ruft den Warenkorb eines Benutzers aus dem Redis Cache ab.
     * Berechnet die Gesamtsumme und das verbleibende Guthaben.
     * Erneuert das Timeout des Warenkorbs bei Zugriff, falls er nicht leer ist.
     * @param userId Die ID des Benutzers, dessen Warenkorb abgerufen werden soll.
     * @return Das {@link Basket}-DTO mit den Artikeln, der Gesamtsumme und dem Restguthaben.
     * @throws NotFoundException wenn der Benutzer nicht existiert.
     * @throws WebApplicationException wenn ein Fehler beim Deserialisieren der Artikel auftritt.
     */
    public Basket getBasket(String userId) {
        // Benutzerdaten abrufen, um das Guthaben zu kennen und Existenz zu prüfen
        UserEntity user = userRepository.findByName(userId);
        if (user == null) {
            throw new NotFoundException("Benutzer nicht gefunden");
        }

        String basketKey = getBasketKey(userId);
        // Alle Artikel (als JSON-Strings) aus dem Hash des Benutzers abrufen
        Map<String, String> itemsMap = hashCommands.hgetall(basketKey);

        Basket basket = new Basket();
        List<Item> itemsList = new ArrayList<>();
        float total = 0.0f;

        // Artikel-JSONs deserialisieren und Gesamtsumme berechnen
        for (Map.Entry<String, String> entry : itemsMap.entrySet()) {
            try {
                Item item = objectMapper.readValue(entry.getValue(), Item.class);
                itemsList.add(item);
                total += item.getPrice() * item.getCount();
            } catch (JsonProcessingException e) {
                // Fehler bei der Deserialisierung ist ein Serverfehler
                throw new WebApplicationException("Fehler beim Lesen des Warenkorbs aus Redis", e, Response.Status.INTERNAL_SERVER_ERROR);
            }
        }

        basket.setItems(itemsList);
        basket.setTotal(total);
        basket.setRemainingBalance(user.getBalance()); // Aktuelles Guthaben des Nutzers setzen

        // Wenn der Warenkorb Artikel enthält, das Timeout erneuern (Aufgabe 3)
        if (!itemsList.isEmpty()) {
            refreshTimeout(basketKey);
        }

        return basket;
    }

    /**
     * Leert den Warenkorb eines Benutzers, indem der entsprechende Redis-Key gelöscht wird.
     * @param userId Die ID des Benutzers, dessen Warenkorb geleert werden soll.
     */
    public void clearBasket(String userId) {
        String basketKey = getBasketKey(userId);
        keyCommands.del(basketKey); // Löscht den gesamten Hash für diesen Warenkorb
    }

    /**
     * Fügt einen Artikel zum Warenkorb eines Benutzers hinzu.
     * Prüft Produkt-ID, Existenz des Artikels im Warenkorb, Nutzerguthaben und die maximale Artikelanzahl.
     * Aktualisiert das Timeout des Warenkorbs.
     * @param userId Die ID des Benutzers.
     * @param productId Die ID des Produkts (aus dem Pfad, zur Validierung).
     * @param item Das hinzuzufügende {@link Item}-DTO (validiert durch @Valid).
     * @return Der aktualisierte {@link Basket}.
     * @throws BadRequestException wenn Produkt-IDs nicht übereinstimmen, Guthaben nicht reicht oder Warenkorb voll ist.
     * @throws NotFoundException wenn der Benutzer nicht existiert.
     * @throws ClientErrorException wenn das Produkt bereits im Warenkorb ist (Status 409).
     * @throws WebApplicationException bei Serialisierungsfehlern.
     */
    public Basket addItemToBasket(
        final String userId,
        final String productId,
        @NotNull @Valid final Item item) {
        // Konsistenzprüfung: Produkt-ID im Pfad muss mit der im Request-Body übereinstimmen
        if (!productId.equals(item.getProductId())) {
            throw new BadRequestException("Produktnummer im Pfad und im Item stimmen nicht überein");
        }

        UserEntity user = userRepository.findByName(userId);
        if (user == null) {
            throw new NotFoundException("Benutzer nicht gefunden");
        }

        String basketKey = getBasketKey(userId);
        // Prüfen, ob der Artikel bereits im Warenkorb ist (ein Artikel kann nur einmal hinzugefügt werden, Anzahl wird ggf. geändert)
        if (hashCommands.hexists(basketKey, productId)) {
            // HTTP 409 Conflict, wenn versucht wird, ein bereits vorhandenes Produkt erneut hinzuzufügen
            throw new ClientErrorException("Produkt bereits im Warenkorb. Nutzen Sie die Update-Funktion, um die Anzahl zu ändern.", 409);
        }

        // Guthabenprüfung (Aufgabe "Prepaid-Zahlungsmethode")
        float itemCost = item.getPrice() * item.getCount();
        if (user.getBalance() < itemCost) {
            throw new BadRequestException("Nicht genügend Guthaben für diesen Artikel");
        }

        // Prüfung der maximalen Anzahl unterschiedlicher Artikel im Warenkorb (Aufgabe 2)
        if (hashCommands.hlen(basketKey) >= MAX_ITEMS_IN_BASKET) {
            throw new ClientErrorException("Der Warenkorb darf nicht mehr als " + MAX_ITEMS_IN_BASKET + " unterschiedliche Artikel enthalten.", Response.Status.CONFLICT);
        }
                
        try {
            // Artikel zu JSON serialisieren und in Redis speichern
            String itemJson = objectMapper.writeValueAsString(item);
            hashCommands.hset(basketKey, productId, itemJson);

            refreshTimeout(basketKey); // Timeout bei jeder Änderung erneuern

            return getBasket(userId); // Aktualisierten Warenkorb zurückgeben
        } catch (JsonProcessingException e) {
            throw new WebApplicationException("Fehler beim Speichern des Items im Warenkorb", e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Entfernt einen Artikel aus dem Warenkorb eines Benutzers.
     * Aktualisiert das Timeout des Warenkorbs.
     * @param userId Die ID des Benutzers.
     * @param productId Die ID des zu entfernenden Produkts.
     * @return Der aktualisierte {@link Basket}.
     * @throws NotFoundException wenn das Produkt nicht im Warenkorb gefunden wurde.
     */
    public Basket removeItemFromBasket(String userId, String productId) {
        String basketKey = getBasketKey(userId);
        if (!hashCommands.hexists(basketKey, productId)) {
            throw new NotFoundException("Produkt nicht im Warenkorb gefunden");
        }

        hashCommands.hdel(basketKey, productId); // Artikel aus dem Hash entfernen
        refreshTimeout(basketKey);

        return getBasket(userId);
    }

    /**
     * Ändert die Anzahl eines bereits im Warenkorb befindlichen Artikels.
     * HINWEIS: Die Implementierung dieser Methode ist im bereitgestellten Code unvollständig.
     * Eine vollständige Implementierung müsste:
     * 1. Prüfen, ob der Artikel existiert.
     * 2. Das neue Item validieren (Anzahl > 0).
     * 3. Das Guthaben des Nutzers prüfen (Gesamtkosten des Warenkorbs mit neuer Anzahl vs. Guthaben).
     * 4. Die Gesamtanzahl aller Artikel im Warenkorb prüfen (darf 10 nicht überschreiten).
     * 5. Das Item in Redis aktualisieren (hset mit dem neuen Item-JSON).
     * 6. Das Timeout erneuern.
     * 7. Den aktualisierten Warenkorb zurückgeben.
     *
     * @param userId Die ID des Benutzers.
     * @param productId Die ID des Produkts, dessen Anzahl geändert werden soll.
     * @param item Das {@link Item}-DTO mit der neuen Anzahl (und ggf. anderen validierten Daten).
     * @return Der aktualisierte {@link Basket}.
     */
    public Basket changeItemCount(String userId, String productId, @NotNull @Valid Item item) {
        // Konsistenzprüfung: Produkt-ID im Pfad muss mit der im Request-Body übereinstimmen
        if (!productId.equals(item.getProductId())) {
            throw new BadRequestException("Produktnummer im Pfad und im Item stimmen nicht überein");
        }

        UserEntity user = userRepository.findByName(userId);
        if (user == null) {
            throw new NotFoundException("Benutzer nicht gefunden: " + userId);
        }

        String basketKey = getBasketKey(userId);
        if (!hashCommands.hexists(basketKey, productId)) {
            throw new NotFoundException("Produkt " + productId + " nicht im Warenkorb gefunden.");
        }

        // Das übergebene 'item' enthält die neue Anzahl und wird für die Aktualisierung verwendet.
        // Die Validierung der Artikelanzahl (>0) erfolgt durch @Valid auf dem Parameter.

        // Alle Artikel sammeln, um Gesamtanzahl und Gesamtkosten neu zu berechnen.
        Map<String, String> basketItemsMap = hashCommands.hgetall(basketKey);
        List<Item> currentItemsInBasket = new ArrayList<>();
        BigDecimal newTotalBasketCost = BigDecimal.ZERO;
        int newTotalItemCount = 0;

        for (Map.Entry<String, String> entry : basketItemsMap.entrySet()) {
            try {
                Item currentItem = objectMapper.readValue(entry.getValue(), Item.class);
                if (entry.getKey().equals(productId)) { // Dies ist der Artikel, dessen Anzahl geändert wird
                    currentItem.setCount(item.getCount()); // Neue Anzahl aus dem Request übernehmen
                    // Preis und Name aus dem Request übernehmen, falls sie sich geändert haben könnten
                    currentItem.setPrice(item.getPrice());
                    currentItem.setProductName(item.getProductName());
                }
                currentItemsInBasket.add(currentItem);
                newTotalItemCount += currentItem.getCount();
                newTotalBasketCost = newTotalBasketCost.add(
                    BigDecimal.valueOf(currentItem.getPrice()).multiply(BigDecimal.valueOf(currentItem.getCount()))
                );
            } catch (JsonProcessingException e) {
                logger.error("Fehler beim Deserialisieren eines Artikels aus dem Warenkorb für Benutzer " + userId, e);
                // Hier könnte man entscheiden, den fehlerhaften Artikel zu ignorieren oder einen Fehler zu werfen
            }
        }

        // Prüfe maximale Artikelanzahl
        if (newTotalItemCount > MAX_ITEMS_IN_BASKET) {
            throw new BadRequestException("Maximale Artikelanzahl von " + MAX_ITEMS_IN_BASKET + " im Warenkorb überschritten. Aktuell: " + newTotalItemCount);
        }

        // Prüfe Guthaben.
        // Hinweis: Die ursprüngliche Prüfung auf user.getPaymentType() wurde entfernt,
        // da UserEntity aktuell keine Methode getPaymentType() besitzt.
        // Die Guthabenprüfung erfolgt nun für alle Benutzer, falls das Guthaben nicht ausreicht.
        if (user.getBalance() == null || BigDecimal.valueOf(user.getBalance()).compareTo(newTotalBasketCost) < 0) {
            throw new WebApplicationException(
                "Nicht genügend Guthaben vorhanden. Benötigt: " + newTotalBasketCost + ", Verfügbar: " + (user.getBalance() != null ? user.getBalance() : "0"),
                Response.Status.PAYMENT_REQUIRED
            );
        }

        // Artikel in Redis aktualisieren
        try {
            String itemJson = objectMapper.writeValueAsString(item); // item enthält bereits die neue Anzahl
            hashCommands.hset(basketKey, productId, itemJson);
        } catch (JsonProcessingException e) {
            logger.error("Fehler beim Serialisieren des Artikels " + productId + " für Benutzer " + userId, e);
            throw new WebApplicationException("Fehler beim Aktualisieren des Warenkorbs", Response.Status.INTERNAL_SERVER_ERROR);
        }

        refreshTimeout(basketKey);
        return getBasket(userId);
    }

    /**
     * Hilfsmethode, um das Timeout für einen Warenkorb-Key in Redis zu erneuern.
     * Diese Methode wird nach jeder relevanten Warenkorb-Aktion aufgerufen, um sicherzustellen,
     * dass der Warenkorb nicht vorzeitig abläuft, solange der Benutzer aktiv ist.
     * Die eigentliche Implementierung würde `keyCommands.expire(basketKey, BASKET_TIMEOUT.getSeconds())` verwenden.
     * @param basketKey Der Redis-Schlüssel des Warenkorbs.
     */
    private void refreshTimeout(String basketKey) {
        // Setzt die Ablauffrist für den Key neu auf BASKET_TIMEOUT Sekunden (Aufgabe 3)
        // Beispiel: keyCommands.expire(basketKey, BASKET_TIMEOUT.getSeconds());
        // Da die Methode hier nur als Platzhalter für die Erklärung dient, erfolgt keine echte Redis-Operation.
        // In der echten Implementierung ist dies entscheidend für das automatische Löschen.
        if (keyCommands != null && basketKey != null && !basketKey.trim().isEmpty()) {
             keyCommands.expire(basketKey, BASKET_TIMEOUT.getSeconds());
        } 
    }
}
