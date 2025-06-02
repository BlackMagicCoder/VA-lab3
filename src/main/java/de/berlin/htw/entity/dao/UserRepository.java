package de.berlin.htw.entity.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import de.berlin.htw.entity.dto.UserEntity;

/**
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
@ApplicationScoped
public class UserRepository {

    @PersistenceContext
    EntityManager entityManager;
    
    public UserEntity findUserById(final Integer id) {
        return entityManager.find(UserEntity.class, id);
    }
    
    /**
     * Findet einen Benutzer anhand seines Namens
     * 
     * @param name Der Name des Benutzers
     * @return Den gefundenen Benutzer oder null, wenn kein Benutzer mit diesem Namen existiert
     */
    public UserEntity findByName(final String name) {
        TypedQuery<UserEntity> query = entityManager.createQuery(
                "SELECT u FROM UserEntity u WHERE u.name = :name", UserEntity.class);
        query.setParameter("name", name);
        try {
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
    
    /**
     * Aktualisiert den Kontostand eines Benutzers
     * 
     * @param user Der zu aktualisierende Benutzer
     * @return Der aktualisierte Benutzer
     */
    @Transactional
    public UserEntity updateUserBalance(final UserEntity user) {
        return entityManager.merge(user);
    }
    
    @Transactional
    public void persistUser(final UserEntity user) {
        entityManager.persist(user);
    }
}