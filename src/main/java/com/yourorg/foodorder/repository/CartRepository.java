package com.yourorg.foodorder.repository;

import com.yourorg.foodorder.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Cart persistence.
 *
 * One cart per user — findByUserId returns an Optional because a user may not
 * have a cart yet (first visit). CartService creates one on demand.
 *
 * JOIN FETCH items + menuItem on the detail query prevents N+1 when rendering
 * CartResponse: without it Hibernate issues one SELECT per CartItem for the
 * menu item name and price (two N+1 paths eliminated in one query).
 */
@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    /**
     * Finds a cart by owner user ID.
     * Returns empty Optional if the user has no cart yet — CartService
     * creates one on first addItemToCart() call.
     */
    @Query("SELECT c FROM Cart c WHERE c.user.id = :userId")
    Optional<Cart> findByUserId(UUID userId);

    /**
     * Finds a cart with all items and their menu items loaded in one query.
     * Used for CartResponse rendering and checkout — avoids N+1.
     *
     * DISTINCT prevents duplicate Cart rows from the JOIN FETCH.
     */
    @Query("SELECT DISTINCT c FROM Cart c " +
           "LEFT JOIN FETCH c.items ci " +
           "LEFT JOIN FETCH ci.menuItem " +
           "WHERE c.user.id = :userId")
    Optional<Cart> findByUserIdWithItems(UUID userId);

    /**
     * Existence check — used before creating a new cart to prevent duplicates.
     */
    boolean existsByUserId(UUID userId);
}