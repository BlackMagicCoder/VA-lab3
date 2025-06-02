package de.berlin.htw.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.berlin.htw.boundary.dto.Basket;
import de.berlin.htw.boundary.dto.Item;
import de.berlin.htw.boundary.dto.Order;
import de.berlin.htw.boundary.dto.Orders;
import de.berlin.htw.entity.dao.OrderRepository;
import de.berlin.htw.entity.dao.UserRepository;
import de.berlin.htw.entity.dto.OrderEntity;
import de.berlin.htw.entity.dto.OrderItemEntity;
import de.berlin.htw.entity.dto.UserEntity;

/**
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
@ApplicationScoped
public class OrderController {

    @Inject
    OrderRepository orderRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    BasketController basketController;
    
    @PersistenceContext
    EntityManager entityManager;

    /**
     * Holt die Liste der abgeschlossenen Bestellungen eines Benutzers
     * 
     * @param username Der Benutzername
     * @return Liste der Bestellungen
     */
    public List<Order> getCompletedOrders(String username) {
        // Benutzer suchen
        UserEntity user = userRepository.findByName(username);
        if (user == null) {
            throw new NotFoundException("Benutzer nicht gefunden: " + username);
        }

        // Bestellungen des Benutzers laden
        List<OrderEntity> orderEntities = orderRepository.findOrdersByUser(user);

        // Konvertiere Entity-Objekte in DTO-Objekte
        return convertToOrderDTOs(orderEntities);
    }

    /**
     * Platziert eine Bestellung mit dem aktuellen Inhalt des Warenkorbs
     * 
     * @param username Der Benutzername
     * @return Die platzierte Bestellung
     * @throws JsonProcessingException Bei JSON-Verarbeitungsfehlern
     */
    @Transactional
    public Order placeOrder(String username) throws JsonProcessingException {
        // Benutzer suchen
        UserEntity user = userRepository.findByName(username);
        if (user == null) {
            throw new NotFoundException("Benutzer nicht gefunden: " + username);
        }

        // Hole den Warenkorb
        Basket basket = basketController.getBasket(username);
        if (basket.getItems().isEmpty()) {
            throw new BadRequestException("Der Warenkorb ist leer");
        }

        // Gesamtsumme berechnen
        float total = basket.getItems().stream()
                .map(item -> item.getPrice() * item.getCount())
                .reduce(0f, Float::sum);

        // Prüfen, ob der Benutzer genug Guthaben hat
        if (user.getBalance() < total) {
            throw new BadRequestException("Nicht genügend Guthaben");
        }

        // Neue Bestellung erstellen
        OrderEntity order = new OrderEntity();
        order.setUser(user);
        order.setTotal(total);
        order.setOrderDate(LocalDateTime.now());

        // Speichere Bestellung, um ID zu generieren
        orderRepository.saveOrder(order);

        // Füge Bestellpositionen hinzu
        List<OrderItemEntity> orderItems = new ArrayList<>();
        for (Item item : basket.getItems()) {
            OrderItemEntity orderItem = new OrderItemEntity();
            orderItem.setOrder(order);
            orderItem.setProductId(item.getProductId());
            orderItem.setProductName(item.getProductName());
            orderItem.setCount(item.getCount());
            orderItem.setPrice(item.getPrice());
            orderItems.add(orderItem);

            // Speichere das OrderItem
            entityManager.persist(orderItem);
        }
        order.setItems(orderItems);

        // Ziehe den Betrag vom Guthaben des Benutzers ab
        user.setBalance(user.getBalance() - total);
        // Aktualisiere den Benutzer
        entityManager.merge(user);

        // Leere den Warenkorb
        basketController.clearBasket(username);

        // Gib die Bestellung zurück
        return convertToOrderDTO(order);
    }

    /**
     * Konvertiert eine Liste von OrderEntity-Objekten in Order-DTOs
     * 
     * @param orderEntities Liste der Entity-Objekte
     * @return Liste der DTO-Objekte
     */
    private List<Order> convertToOrderDTOs(List<OrderEntity> orderEntities) {
        List<Order> orders = new ArrayList<>();
        for (OrderEntity entity : orderEntities) {
            orders.add(convertToOrderDTO(entity));
        }
        return orders;
    }

    /**
     * Konvertiert ein OrderEntity-Objekt in ein Order-DTO
     * 
     * @param entity Das Entity-Objekt
     * @return Das DTO-Objekt
     */
    private Order convertToOrderDTO(OrderEntity entity) {
        Order order = new Order();

        // Konvertiere OrderItems zu Items
        List<Item> items = new ArrayList<>();
        for (OrderItemEntity itemEntity : entity.getItems()) {
            Item item = new Item();
            item.setProductId(itemEntity.getProductId());
            item.setProductName(itemEntity.getProductName());
            item.setCount(itemEntity.getCount());
            item.setPrice(itemEntity.getPrice());
            items.add(item);
        }

        order.setItems(items);
        return order;
    }

    public Orders todo() {
        throw new InternalServerErrorException("TODO");
    }
}
