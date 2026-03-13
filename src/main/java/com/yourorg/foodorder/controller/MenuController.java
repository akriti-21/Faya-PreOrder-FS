package com.yourorg.foodorder.controller;

import com.yourorg.foodorder.dto.request.MenuItemRequest;
import com.yourorg.foodorder.dto.response.ApiResponse;
import com.yourorg.foodorder.dto.response.MenuItemResponse;
import com.yourorg.foodorder.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * MenuController — menu item endpoints scoped to a vendor.
 *
 * POST /api/v1/vendors/{vendorId}/menu     — admin adds a menu item
 * GET  /api/v1/vendors/{vendorId}/menu     — public available menu for a vendor
 * GET  /api/v1/vendors/{vendorId}/menu/all — admin full menu (includes unavailable)
 *
 * All paths are nested under /vendors/{vendorId} to reflect the ownership
 * relationship: a menu item belongs to a vendor, so the vendor ID is always
 * required context. This also allows SecurityConfig to apply vendor-scoped
 * rules in the future (e.g., only the vendor owner may add items).
 */
@RestController
@RequestMapping("/api/v1/vendors/{vendorId}/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /**
     * POST /api/v1/vendors/{vendorId}/menu
     * Add a menu item to a vendor. Admin only.
     *
     * Returns 201 Created with the persisted MenuItemResponse.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MenuItemResponse>> addMenuItem(
            @PathVariable UUID vendorId,
            @Valid @RequestBody MenuItemRequest request) {

        MenuItemResponse item = menuService.addMenuItem(vendorId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Menu item added successfully.", item));
    }

    /**
     * GET /api/v1/vendors/{vendorId}/menu
     * Get the available (public) menu for a vendor.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<MenuItemResponse>>> getMenu(
            @PathVariable UUID vendorId) {

        List<MenuItemResponse> menu = menuService.getMenuByVendor(vendorId);
        return ResponseEntity.ok(
                ApiResponse.success("Menu retrieved successfully.", menu));
    }

    /**
     * GET /api/v1/vendors/{vendorId}/menu/all
     * Get the full menu including unavailable items. Admin only.
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<MenuItemResponse>>> getFullMenu(
            @PathVariable UUID vendorId) {

        List<MenuItemResponse> menu = menuService.getFullMenuByVendor(vendorId);
        return ResponseEntity.ok(
                ApiResponse.success("Full menu retrieved successfully.", menu));
    }
}