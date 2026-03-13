package com.yourorg.foodorder.controller;

import com.yourorg.foodorder.dto.request.AddCartItemRequest;
import com.yourorg.foodorder.dto.request.CheckoutRequest;
import com.yourorg.foodorder.dto.request.UpdateCartItemRequest;
import com.yourorg.foodorder.dto.response.ApiResponse;
import com.yourorg.foodorder.dto.response.CartResponse;
import com.yourorg.foodorder.dto.response.OrderResponse;
import com.yourorg.foodorder.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * CartController — cart CRUD and checkout endpoints.
 *
 * All endpoints:
 *   - Require authentication (ROLE_USER via @PreAuthorize)
 *   - Return ApiResponse envelope
 *   - Contain NO business logic (delegated entirely to CartService)
 *
 * URL prefix: /api/v1/cart (consistent with project v1 API versioning)
 *
 * Security note:
 *   The SecurityConfig anyRequest().authenticated() catch-all would protect
 *   these endpoints, but @PreAuthorize("hasRole('USER')") is explicit here
 *   for clarity and to ensure method-level security is active even if the
 *   URL-level config changes.
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * GET /api/v1/cart
     * Returns the authenticated user's cart with all items and total.
     * Creates an empty cart if the user has none yet.
     */
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CartResponse>> getCart() {
        CartResponse cart = cartService.getCartForUser();
        return ResponseEntity.ok(
                ApiResponse.success("Cart retrieved successfully.", cart));
    }

    /**
     * POST /api/v1/cart/items
     * Adds a menu item to the cart. Merges if item already present.
     * Returns 201 Created with the updated cart.
     */
    @PostMapping("/items")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @Valid @RequestBody AddCartItemRequest request) {

        CartResponse cart = cartService.addItemToCart(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Item added to cart.", cart));
    }

    /**
     * PUT /api/v1/cart/items/{itemId}
     * Updates the quantity of an existing cart item.
     * Returns 200 with the updated cart.
     */
    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        CartResponse cart = cartService.updateCartItem(itemId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Cart item updated.", cart));
    }

    /**
     * DELETE /api/v1/cart/items/{itemId}
     * Removes a single item from the cart.
     * Returns 200 with the updated cart (not 204 — ApiResponse envelope expected).
     */
    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @PathVariable UUID itemId) {

        CartResponse cart = cartService.removeCartItem(itemId);
        return ResponseEntity.ok(
                ApiResponse.success("Item removed from cart.", cart));
    }

    /**
     * DELETE /api/v1/cart
     * Clears all items from the cart. Cart row is retained for reuse.
     * Returns 200 with an empty CartResponse.
     */
    @DeleteMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CartResponse>> clearCart() {
        CartResponse cart = cartService.clearCart();
        return ResponseEntity.ok(
                ApiResponse.success("Cart cleared.", cart));
    }

    /**
     * POST /api/v1/cart/checkout
     * Converts the cart into an Order and clears it.
     * Returns 201 Created with the full OrderResponse.
     *
     * On success:
     *   - A new Order is created in PENDING status
     *   - Cart items are deleted
     *   - The Cart row remains (empty, ready for next session)
     */
    @PostMapping("/checkout")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request) {

        OrderResponse order = cartService.checkoutCart(
                request.deliveryAddress(), request.notes());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Order placed successfully.", order));
    }
}