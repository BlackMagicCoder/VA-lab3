package de.berlin.htw.control;

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
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;

/**
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
@Dependent
public class BasketController {

    // Konstante für das Timeout des Warenkorbs (2 Minuten)
    private static final Duration BASKET_TIMEOUT = Duration.ofMinutes(2);
    
    @Inject
    protected RedisDataSource redisDS;
    
    @Inject
    protected UserRepository userRepository;
    
    @Inject
    protected Validator validator;
    
    @Inject
    protected ObjectMapper objectMapper;
    
    // Redis Commands für Hash-Operationen (für den Warenkorb)
    protected HashCommands<String, String, String> hashCommands;
    
    // Redis Commands für Key-Operationen (zum Setzen des Timeouts)
    protected KeyCommands<String> keyCommands;
    
    @PostConstruct
    protected void init() {
        hashCommands = redisDS.hash(String.class);
        keyCommands = redisDS.key();
    }
    
    /**
     * Hilfsmethode, um den Redis-Key für einen Benutzer zu erstellen
     */
    private String getBasketKey(String userId) {
        return "basket:" + userId;
    }
    
    /**
     * Holt den Warenkorb eines Benutzers aus dem Redis Cache
     */
    public Basket getBasket(String userId) {
        // Hole den Benutzer aus der Datenbank
        UserEntity user = userRepository.findByName(userId);
        if (user == null) {
            throw new NotFoundException("Benutzer nicht gefunden");
        }
        
        String basketKey = getBasketKey(userId);
        Map<String, String> itemsMap = hashCommands.hgetall(basketKey);
        
        // Erstelle den Warenkorb mit den Items
        Basket basket = new Basket();
        List<Item> items = new ArrayList<>();
        float total = 0.0f;
        
        // Deserialisiere die Items aus dem Redis Cache
        for (Map.Entry<String, String> entry : itemsMap.entrySet()) {
            try {
                Item item = objectMapper.readValue(entry.getValue(), Item.class);
                items.add(item);
                total += item.getPrice() * item.getCount();
            } catch (JsonProcessingException e) {
                throw new WebApplicationException("Fehler beim Lesen des Warenkorbs", 500);
            }
        }
        
        // Setze die Werte im Warenkorb
        basket.setItems(items);
        basket.setTotal(total);
        basket.setRemainingBalance(user.getBalance());
        
        // Erneuere das Timeout, wenn der Warenkorb nicht leer ist
        if (!items.isEmpty()) {
            refreshTimeout(basketKey);
        }
        
        return basket;
    }
    
    /**
     * Leert den Warenkorb eines Benutzers
     */
    public void clearBasket(String userId) {
        String basketKey = getBasketKey(userId);
        // Lösche alle Einträge im Warenkorb
        keyCommands.del(basketKey);
    }
    
    /**
     * Fügt ein Item zum Warenkorb hinzu
     */
    public Basket addItemToBasket(String userId, String productId, @NotNull @Valid Item item) {
        // Validiere die Produktnummer im Pfad mit der im Item
        if (!productId.equals(item.getProductId())) {
            throw new BadRequestException("Produktnummer im Pfad und im Item stimmen nicht überein");
        }
        
        // Hole den Benutzer und prüfe, ob genug Guthaben vorhanden ist
        UserEntity user = userRepository.findByName(userId);
        if (user == null) {
            throw new NotFoundException("Benutzer nicht gefunden");
        }
        
        // Prüfe, ob das Item bereits im Warenkorb ist
        String basketKey = getBasketKey(userId);
        if (hashCommands.hexists(basketKey, productId)) {
            throw new ClientErrorException("Produkt bereits im Warenkorb", 409);
        }
        
        // Prüfe, ob der Benutzer genügend Guthaben hat
        float itemCost = item.getPrice() * item.getCount();
        if (user.getBalance() < itemCost) {
            throw new BadRequestException("Nicht genügend Guthaben");
        }
        
        // Hole den aktuellen Warenkorb, um die Gesamtzahl der Items zu prüfen
        Basket currentBasket = getBasket(userId);
        // Prüfe, ob das Hinzufügen des Items die maximale Anzahl von 10 überschreiten würde
        int totalItems = currentBasket.getItems().stream()
                .mapToInt(Item::getCount)
                .sum() + item.getCount();
        
        if (totalItems > 10) {
            throw new BadRequestException("Der Warenkorb darf nicht mehr als 10 Artikel enthalten");
        }
                
        // Speichere das Item im Redis Cache
        try {
            String itemJson = objectMapper.writeValueAsString(item);
            hashCommands.hset(basketKey, productId, itemJson);
            
            // Setze ein Timeout für den Warenkorb
            refreshTimeout(basketKey);
            
            // Hole den aktualisierten Warenkorb
            return getBasket(userId);
        } catch (JsonProcessingException e) {
            throw new WebApplicationException("Fehler beim Speichern des Items", 500);
        }
    }
    
    /**
     * Entfernt ein Item aus dem Warenkorb
     */
    public Basket removeItemFromBasket(String userId, String productId) {
        // Prüfe, ob das Item im Warenkorb ist
        String basketKey = getBasketKey(userId);
        if (!hashCommands.hexists(basketKey, productId)) {
            throw new NotFoundException("Produkt nicht im Warenkorb");
        }
        
        // Lösche das Item aus dem Warenkorb
        hashCommands.hdel(basketKey, productId);
        
        // Erneuere das Timeout
        refreshTimeout(basketKey);
        
        // Hole den aktualisierten Warenkorb
        return getBasket(userId);
    }
    
    /**
     * Ändert die Anzahl eines Items im Warenkorb
     */
    public Basket changeItemCount(String userId, String productId, @NotNull @Valid Item item) {
        // Validiere die Produktnummer im Pfad mit der im Item
        if (!productId.equals(item.getProductId())) {
            throw new BadRequestException("Produktnummer im Pfad und im Item stimmen nicht überein");
        }
        
        String basketKey = getBasketKey(userId);
        // Prüfe, ob das Item im Warenkorb ist
        if (!hashCommands.hexists(basketKey, productId)) {
            throw new NotFoundException("Produkt nicht im Warenkorb");
        }
        
        // Hole den Benutzer und das aktuelle Item, um zu prüfen, ob genug Guthaben vorhanden ist
        UserEntity user = userRepository.findByName(userId);
        if (user == null) {
            throw new NotFoundException("Benutzer nicht gefunden");
        }
        
        // Hole das aktuelle Item aus dem Warenkorb
        String itemJson = hashCommands.hget(basketKey, productId);
        try {
            Item currentItem = objectMapper.readValue(itemJson, Item.class);
            
            // Berechne den Preisunterschied
            float currentCost = currentItem.getPrice() * currentItem.getCount();
            float newCost = item.getPrice() * item.getCount();
            float priceDifference = newCost - currentCost;
            
            // Prüfe, ob der Benutzer genügend Guthaben hat (falls der Preis steigt)
            if (priceDifference > 0 && user.getBalance() < priceDifference) {
                throw new BadRequestException("Nicht genügend Guthaben für die Erhöhung der Anzahl");
            }
            
            // Speichere das aktualisierte Item
            String updatedItemJson = objectMapper.writeValueAsString(item);
            hashCommands.hset(basketKey, productId, updatedItemJson);
            
            // Erneuere das Timeout
            refreshTimeout(basketKey);
            
            // Hole den aktualisierten Warenkorb
            return getBasket(userId);
        } catch (JsonProcessingException e) {
            throw new WebApplicationException("Fehler beim Verarbeiten des Items", 500);
        }
    }
    
    /**
     * Erneuert das Timeout für den Warenkorb
     */
    private void refreshTimeout(String basketKey) {
        keyCommands.expire(basketKey, BASKET_TIMEOUT);
    }
    
    /**
     * Setzt einen Testwert in Redis
     * Diese Methode wird nur für Tests verwendet
     * 
     * @param key Der Schlüssel des Werts
     * @param value Der zu setzende Wert
     */
    public void setTestValue(String key, int value) {
        // Verwende Redis Value Commands für einfache Werte
        var valueCommands = redisDS.value(Integer.class);
        valueCommands.set(key, value);
    }
}
