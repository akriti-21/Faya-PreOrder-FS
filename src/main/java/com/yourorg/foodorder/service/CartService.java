package com.yourorg.foodorder.service;

import com.yourorg.foodorder.domain.*;
import com.yourorg.foodorder.dto.request.AddCartItemRequest;
import com.yourorg.foodorder.dto.request.UpdateCartItemRequest;
import com.yourorg.foodorder.dto.response.CartResponse;
import com.yourorg.foodorder.dto.response.OrderResponse;
import com.yourorg.foodorder.exception.BusinessException;
import com.yourorg.foodorder.exception.ResourceNotFoundException;
import com.yourorg.foodorder.repository.CartItemRepository;
import com.yourorg.foodorder.repository.CartRepository;
import com.yourorg.foodorder.repository.MenuItemRepository;
import com.yourorg.foodorder.repository.OrderRepository;
import com.yourorg.foodorder.repository.UserRepository;
import com.yourorg.foodorder.security.SecurityPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CartService — all cart lifecycle operations.
 *
 * Cart-per-user model:
 *   Each user has at most one Cart row. getOrCreateCart() is the single entry
 *   point for resolving the cart — it creates one on first access rather than
 *   requiring a separate "create cart" API call.
 *
 * Merge-on-add:
 *   Adding an item that already exists in the cart increments the quantity
 *   and refreshes the price snapshot rather than creating a duplicate row.
 *   This keeps the DB clean and avoids confusing the user with two rows for
 *   the same item.
 *
 * Checkout transaction:
 *   The entire checkoutCart() method is a single @Transactional unit.
 *   Steps:
 *     1. Load cart with items (JOIN FETCH — single query)
 *     2. Validate cart is not empty
 *     3. Validate all items still available (batch fetch from menu_items)
 *     4. Validate single-vendor constraint
 *     5. Calculate total from CartItem price snapshots
 *     6. Build Order + OrderItems (CascadeType.ALL — one save())
 *     7. Clear cart items (bulk DELETE)
 *     8. Return OrderResponse
 *   If any step fails, the full transaction rolls back — no partial order,
 *   no cleared cart.
 *
 * Delivery address:
 *   Checkout requires a deliveryAddress. It is not stored on the Cart — the
 *   user provides it at checkout time (OrderCheckoutRequest). This matches
 *   real-world UX: address is confirmed at payment, not at add-to-cart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository     cartRepository;
    private final CartItemRepository cartItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderRepository    orderRepository;
    private final UserRepository     userRepository;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the current user's cart with all items and menu item details.
     * Creates an empty cart if the user has none yet.
     *
     * @return CartResponse with items and computed total
     */
    @Transactional
    public CartResponse getCartForUser() {
        UUID userId = currentUserId();
        Cart cart = getOrCreateCartWithItems(userId);
        return CartResponse.from(cart);
    }

    /**
     * Adds a menu item to the current user's cart.
     *
     * Business rules:
     *   - Menu item must exist and be available (not deleted, not unavailable)
     *   - If the item is already in the cart, quantities are merged (summed)
     *     and the price snapshot is refreshed to the current menu price
     *   - The cart is created automatically if it doesn't exist yet
     *
     * @param request validated AddCartItemRequest
     * @return updated CartResponse
     * @throws ResourceNotFoundException if the menu item does not exist or is unavailable
     */
    @Transactional
    public CartResponse addItemToCart(AddCartItemRequest request) {
        UUID userId = currentUserId();

        // Validate menu item exists and is available
        MenuItem menuItem = menuItemRepository.findActiveById(request.menuItemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MenuItem", "id", request.menuItemId()));

        // Get or create cart (no items JOIN FETCH needed — we manipulate items below)
        Cart cart = getOrCreateCart(userId);

        // Re-load with items for merge check
        Cart cartWithItems = cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "userId", userId));

        Optional<CartItem> existing = cartWithItems.findItemByMenuItemId(request.menuItemId());

        if (existing.isPresent()) {
            // ── Merge: increment quantity and refresh price snapshot ───────────
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + request.quantity());
            item.setPrice(menuItem.getPrice());   // refresh snapshot
            log.debug("Cart item merged: cartId={}, menuItemId={}, newQty={}",
                    cartWithItems.getId(), request.menuItemId(), item.getQuantity());
        } else {
            // ── Add new line item ─────────────────────────────────────────────
            CartItem newItem = CartItem.builder()
                    .menuItem(menuItem)
                    .quantity(request.quantity())
                    .price(menuItem.getPrice())   // snapshot current price
                    .build();
            cartWithItems.addItem(newItem);       // sets cart FK on item
            log.debug("Cart item added: cartId={}, menuItemId={}, qty={}",
                    cartWithItems.getId(), request.menuItemId(), request.quantity());
        }

        Cart saved = cartRepository.save(cartWithItems);
        log.info("Cart updated (add): userId={}, cartId={}, items={}",
                userId, saved.getId(), saved.getItems().size());

        // Re-fetch with JOIN FETCH for response to avoid LAZY issues
        return CartResponse.from(cartRepository.findByUserIdWithItems(userId)
                .orElseThrow());
    }

    /**
     * Updates the quantity of an existing cart item.
     *
     * The CartItem must belong to the authenticated user's cart — CartService
     * enforces ownership (returns 404 if item belongs to another user's cart).
     *
     * @param itemId  UUID of the CartItem to update
     * @param request validated UpdateCartItemRequest
     * @return updated CartResponse
     * @throws ResourceNotFoundException if the item does not exist or belongs to another user
     */
    @Transactional
    public CartResponse updateCartItem(UUID itemId, UpdateCartItemRequest request) {
        UUID userId = currentUserId();
        CartItem item = requireOwnedCartItem(itemId, userId);

        item.setQuantity(request.quantity());
        cartItemRepository.save(item);

        log.info("Cart item updated: userId={}, itemId={}, qty={}", userId, itemId, request.quantity());

        return CartResponse.from(cartRepository.findByUserIdWithItems(userId).orElseThrow());
    }

    /**
     * Removes a single item from the current user's cart.
     *
     * Ownership is enforced — returns 404 if the item belongs to another user.
     *
     * @param itemId UUID of the CartItem to remove
     * @return updated CartResponse (without the removed item)
     * @throws ResourceNotFoundException if the item does not exist or belongs to another user
     */
    @Transactional
    public CartResponse removeCartItem(UUID itemId) {
        UUID userId = currentUserId();
        CartItem item = requireOwnedCartItem(itemId, userId);

        Cart cart = item.getCart();
        cart.getItems().remove(item);  // orphanRemoval triggers DELETE
        cartRepository.save(cart);

        log.info("Cart item removed: userId={}, itemId={}", userId, itemId);

        return CartResponse.from(cartRepository.findByUserIdWithItems(userId).orElseThrow());
    }

    /**
     * Removes all items from the current user's cart.
     * The Cart row itself is retained for reuse (stable cart ID across sessions).
     *
     * @return empty CartResponse
     */
    @Transactional
    public CartResponse clearCart() {
        UUID userId = currentUserId();
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "userId", userId));

        cartItemRepository.deleteAllByCartId(cart.getId());
        cart.clearItems();  // keep in-memory state consistent

        log.info("Cart cleared: userId={}, cartId={}", userId, cart.getId());

        return CartResponse.from(cartRepository.findByUserIdWithItems(userId).orElseThrow());
    }

    /**
     * Converts the current user's cart into an Order.
     *
     * Transaction steps (atomic — all or nothing):
     *   1. Load cart with items + menuItem details (JOIN FETCH)
     *   2. Validate cart is not empty
     *   3. Batch-validate all items are still available
     *   4. Validate single-vendor constraint
     *   5. Calculate total from CartItem price snapshots
     *   6. Build Order + OrderItems (CascadeType.ALL)
     *   7. Persist Order (saves items via cascade)
     *   8. Clear cart items (bulk JPQL DELETE)
     *   9. Return OrderResponse
     *
     * @param deliveryAddress the address to deliver the order to
     * @param notes optional order notes
     * @return OrderResponse of the newly created order
     * @throws BusinessException (409)   if cart is empty or items span multiple vendors
     * @throws ResourceNotFoundException if any item is no longer available
     */
    @Transactional
    public OrderResponse checkoutCart(String deliveryAddress, String notes) {
        UUID userId = currentUserId();
        User user   = currentUser();

        // ── Step 1: Load cart with items ──────────────────────────────────────
        Cart cart = cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new BusinessException("No cart found for user."));

        // ── Step 2: Validate cart not empty ───────────────────────────────────
        if (cart.getItems().isEmpty()) {
            throw new BusinessException("Cannot checkout with an empty cart.");
        }

        // ── Step 3: Batch-validate items still available ──────────────────────
        Set<UUID> cartItemIds = cart.getItems().stream()
                .map(ci -> ci.getMenuItem().getId())
                .collect(Collectors.toSet());

        List<MenuItem> availableItems = menuItemRepository.findAvailableByIds(cartItemIds);
        Map<UUID, MenuItem> availableById = availableItems.stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));

        if (availableItems.size() != cartItemIds.size()) {
            Set<UUID> unavailable = new HashSet<>(cartItemIds);
            unavailable.removeAll(availableById.keySet());
            throw new BusinessException(
                "Some cart items are no longer available: " + unavailable +
                ". Please remove them before checking out.");
        }

        // ── Step 4: Validate single-vendor constraint ─────────────────────────
        Set<UUID> vendorIds = availableItems.stream()
                .map(mi -> mi.getVendor().getId())
                .collect(Collectors.toSet());

        if (vendorIds.size() > 1) {
            throw new BusinessException(
                "All cart items must belong to the same vendor. " +
                "Found items from " + vendorIds.size() + " vendors. " +
                "Clear your cart and add items from a single vendor.");
        }

        Vendor vendor = availableItems.get(0).getVendor();

        // ── Step 5: Calculate total from cart price snapshots ─────────────────
        BigDecimal total = cart.getItems().stream()
                .map(CartItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Step 6: Build Order entity ────────────────────────────────────────
        Order order = Order.builder()
                .user(user)
                .vendor(vendor)
                .status(OrderStatus.PENDING)
                .totalAmount(total)
                .deliveryAddress(deliveryAddress)
                .notes(notes)
                .build();

        // ── Step 7: Build OrderItems from CartItems ───────────────────────────
        for (CartItem cartItem : cart.getItems()) {
            OrderItem orderItem = OrderItem.builder()
                    .menuItem(availableById.get(cartItem.getMenuItem().getId()))
                    .quantity(cartItem.getQuantity())
                    .price(cartItem.getPrice())   // use cart snapshot, not live price
                    .build();
            order.addItem(orderItem);
        }

        // ── Step 8: Persist order (cascade saves all OrderItems) ──────────────
        Order saved = orderRepository.save(order);
        log.info("Checkout complete: orderId={}, userId={}, vendorId={}, total={}, items={}",
                saved.getId(), userId, vendor.getId(), total, saved.getItems().size());

        // ── Step 9: Clear cart ────────────────────────────────────────────────
        cartItemRepository.deleteAllByCartId(cart.getId());
        cart.clearItems();
        cartRepository.save(cart);

        return OrderResponse.from(saved);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the existing Cart for the user, or creates and persists a new one.
     * Does NOT load items — use getOrCreateCartWithItems() for response rendering.
     */
    private Cart getOrCreateCart(UUID userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
            Cart newCart = Cart.builder().user(user).build();
            Cart saved = cartRepository.save(newCart);
            log.info("New cart created: userId={}, cartId={}", userId, saved.getId());
            return saved;
        });
    }

    /**
     * Like getOrCreateCart() but returns the cart with items JOIN FETCHed.
     */
    private Cart getOrCreateCartWithItems(UUID userId) {
        getOrCreateCart(userId); // ensure cart exists
        return cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "userId", userId));
    }

    /**
     * Loads a CartItem and validates it belongs to the authenticated user's cart.
     * Returns 404 instead of 403 — avoids leaking the existence of other users' carts.
     */
    private CartItem requireOwnedCartItem(UUID itemId, UUID userId) {
        CartItem item = cartItemRepository.findByIdWithDetails(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));

        if (!item.getCart().getUser().getId().equals(userId)) {
            // Return 404 — don't reveal that the item exists but belongs to another user
            throw new ResourceNotFoundException("CartItem", "id", itemId);
        }
        return item;
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SecurityPrincipal principal = (SecurityPrincipal) auth.getPrincipal();
        return principal.getUserId();
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SecurityPrincipal principal = (SecurityPrincipal) auth.getPrincipal();
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "email", principal.getUsername()));
    }
}