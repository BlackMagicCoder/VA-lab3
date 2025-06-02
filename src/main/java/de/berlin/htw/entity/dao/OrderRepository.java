package de.berlin.htw.entity.dao;

import java.util.List;

import de.berlin.htw.entity.dto.OrderEntity;
import de.berlin.htw.entity.dto.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

/**
 * Repository für Bestellungen
 */
@ApplicationScoped
public class OrderRepository {

    @PersistenceContext
    EntityManager entityManager;
    
    /**
     * Findet alle Bestellungen eines Benutzers
     * 
     * @param user Der Benutzer, dessen Bestellungen gesucht werden
     * @return Eine Liste aller Bestellungen des Benutzers
     */
    public List<OrderEntity> findOrdersByUser(UserEntity user) {
        TypedQuery<OrderEntity> query = entityManager.createQuery(
                "SELECT o FROM OrderEntity o WHERE o.user = :user ORDER BY o.orderDate DESC", 
                OrderEntity.class);
        query.setParameter("user", user);
        return query.getResultList();
    }
    
    /**
     * Speichert eine neue Bestellung in der Datenbank
     * 
     * @param order Die zu speichernde Bestellung
     * @return Die gespeicherte Bestellung mit ID
     */
    @Transactional
    public OrderEntity saveOrder(OrderEntity order) {
        entityManager.persist(order);
        return order;
    }
}
