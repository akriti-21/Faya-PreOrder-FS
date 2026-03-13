package com.yourorg.foodorder.repository;

import com.yourorg.foodorder.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CartItem persistence.
 *
 * Most mutations go through Cart.getItems() (cascade path).
 * These direct queries exist for targeted operations that don't require
 * loading the full Cart entity first.
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    /**
     * All items in a cart with their menu items loaded.
     * JOIN FETCH prevents N+1 on menuItem when building CartItemResponse.
     */
    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.menuItem " +
           "WHERE ci.cart.id = :cartId " +
           "ORDER BY ci.createdAt ASC")
    List<CartItem> findByCartId(UUID cartId);

    /**
     * Finds a specific cart item by its ID, with menuItem JOIN FETCHed.
     * Used by updateCartItem() and removeCartItem() to validate ownership.
     */
    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.menuItem JOIN FETCH ci.cart " +
           "WHERE ci.id = :itemId")
    Optional<CartItem> findByIdWithDetails(UUID itemId);

    /**
     * Bulk delete all items for a cart — used by clearCart() and checkout.
     * More efficient than loading entities and calling deleteAll().
     *
     * @Modifying + @Transactional(caller-managed): the caller (CartService)
     * is always within a @Transactional boundary when calling this.
     */
    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteAllByCartId(UUID cartId);

    /** Count of items in a cart — used by checkout to validate non-empty. */
    @Query("SELECT COUNT(ci) FROM CartItem ci WHERE ci.cart.id = :cartId")
    long countByCartId(UUID cartId);
}