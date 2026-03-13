package com.yourorg.foodorder.service;

import com.yourorg.foodorder.domain.*;
import com.yourorg.foodorder.dto.request.OrderItemRequest;
import com.yourorg.foodorder.dto.request.OrderRequest;
import com.yourorg.foodorder.dto.response.OrderResponse;
import com.yourorg.foodorder.exception.BusinessException;
import com.yourorg.foodorder.exception.ResourceNotFoundException;
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
 * OrderService — the core order placement and retrieval logic.
 *
 * placeOrder() design:
 *
 *   1. Resolve the authenticated user from SecurityContext.
 *   2. Batch-fetch all requested MenuItems in ONE query (MenuItemRepository.findAvailableByIds).
 *      This prevents N+1 queries when an order has many items.
 *   3. Validate completeness — all requested item IDs must be in the result set.
 *      Missing IDs indicate unavailable or non-existent items.
 *   4. Validate single-vendor constraint — all items must belong to the same vendor.
 *      Mixed-vendor orders are not supported in this iteration.
 *   5. Calculate total using BigDecimal arithmetic (no float rounding errors).
 *   6. Build Order entity with CascadeType.ALL items — one save() persists everything.
 *   7. Return OrderResponse with full item detail.
 *
 * Transaction boundary:
 *   The entire placeOrder() method is @Transactional. If any step fails
 *   (DB constraint, optimistic lock, etc.) the whole operation rolls back.
 *   No partial orders can exist.
 *
 * getOrdersForUser():
 *   Returns a list of order summaries (no items) — lighter response for
 *   the "my orders" list view. Clients call getOrderDetails() for line items.
 *
 * getOrderDetails():
 *   Security invariant: a user may only retrieve their own orders.
 *   Admins may retrieve any order (enforced via @PreAuthorize at controller).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository    orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository     userRepository;

    /**
     * Places a new order for the authenticated user.
     *
     * @param request validated OrderRequest containing item IDs, quantities, and address
     * @return OrderResponse with full item detail and computed total
     * @throws ResourceNotFoundException if any requested menu item does not exist or is unavailable
     * @throws BusinessException (409)   if items span multiple vendors
     */
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {

        // ── Step 1: Resolve authenticated user ────────────────────────────────
        User user = currentUser();

        // ── Step 2: Batch-fetch all requested menu items (single query) ───────
        Set<UUID> requestedIds = request.items().stream()
                .map(OrderItemRequest::menuItemId)
                .collect(Collectors.toSet());

        List<MenuItem> foundItems = menuItemRepository.findAvailableByIds(requestedIds);

        // ── Step 3: Validate all requested items were found ───────────────────
        if (foundItems.size() != requestedIds.size()) {
            Set<UUID> foundIds = foundItems.stream()
                    .map(MenuItem::getId)
                    .collect(Collectors.toSet());
            Set<UUID> missingIds = new HashSet<>(requestedIds);
            missingIds.removeAll(foundIds);
            throw new ResourceNotFoundException(
                "One or more menu items are unavailable or do not exist: " + missingIds);
        }

        // ── Step 4: Validate single-vendor constraint ─────────────────────────
        Map<UUID, MenuItem> itemById = foundItems.stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));

        Set<UUID> vendorIds = foundItems.stream()
                .map(item -> item.getVendor().getId())
                .collect(Collectors.toSet());

        if (vendorIds.size() > 1) {
            throw new BusinessException(
                "All items in an order must belong to the same vendor. " +
                "Found items from " + vendorIds.size() + " different vendors.");
        }

        Vendor vendor = foundItems.get(0).getVendor();

        // ── Step 5: Calculate total amount ────────────────────────────────────
        BigDecimal total = request.items().stream()
                .map(req -> {
                    MenuItem item = itemById.get(req.menuItemId());
                    return item.getPrice().multiply(BigDecimal.valueOf(req.quantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Step 6: Build Order entity ────────────────────────────────────────
        Order order = Order.builder()
                .user(user)
                .vendor(vendor)
                .status(OrderStatus.PENDING)
                .totalAmount(total)
                .deliveryAddress(request.deliveryAddress())
                .notes(request.notes())
                .build();

        // ── Step 7: Build and attach OrderItems ───────────────────────────────
        for (OrderItemRequest req : request.items()) {
            MenuItem menuItem = itemById.get(req.menuItemId());
            OrderItem orderItem = OrderItem.builder()
                    .menuItem(menuItem)
                    .quantity(req.quantity())
                    .price(menuItem.getPrice())   // snapshot current price
                    .build();
            order.addItem(orderItem);             // sets order FK on item
        }

        // ── Step 8: Persist (CascadeType.ALL saves order + all items) ─────────
        Order saved = orderRepository.save(order);
        log.info("Order placed: id={}, userId={}, vendorId={}, total={}, items={}",
                saved.getId(), user.getId(), vendor.getId(), total, saved.getItems().size());

        return OrderResponse.from(saved);
    }

    /**
     * Returns all orders for the authenticated user, newest first.
     * Returns summaries (no item list) — use getOrderDetails() for line items.
     *
     * @return list of OrderResponse summaries
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersForUser() {
        UUID userId = currentUserId();
        return orderRepository.findByUserId(userId)
                .stream()
                .map(OrderResponse::summary)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the full detail of a specific order, including all line items.
     *
     * Security: the order's customer_id must match the authenticated user's ID
     * unless the caller has ROLE_ADMIN. Controller enforces admin access;
     * this method enforces user ownership.
     *
     * @param orderId UUID of the order
     * @return full OrderResponse with items
     * @throws ResourceNotFoundException if the order does not exist
     * @throws BusinessException (403)   if the order belongs to a different user
     *         and the caller is not an admin (determined by the boolean parameter)
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(UUID orderId, boolean isAdmin) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!isAdmin) {
            UUID currentUserId = currentUserId();
            if (!order.getUser().getId().equals(currentUserId)) {
                // Return 404 instead of 403 — don't confirm the order exists to other users
                throw new ResourceNotFoundException("Order", "id", orderId);
            }
        }

        return OrderResponse.from(order);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SecurityPrincipal principal = (SecurityPrincipal) auth.getPrincipal();
        return principal.getUserId();
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SecurityPrincipal principal = (SecurityPrincipal) auth.getPrincipal();
        UUID userId = principal.getUserId();
        // Re-fetch from DB — principal snapshot may not have the latest user state
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}