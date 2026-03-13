package com.yourorg.foodorder.service;

import com.yourorg.foodorder.domain.MenuItem;
import com.yourorg.foodorder.domain.Vendor;
import com.yourorg.foodorder.dto.request.MenuItemRequest;
import com.yourorg.foodorder.dto.response.MenuItemResponse;
import com.yourorg.foodorder.exception.ResourceNotFoundException;
import com.yourorg.foodorder.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MenuService — menu item lifecycle for a vendor.
 *
 * Responsibilities:
 *   addMenuItem()     — admin adds an item to a vendor's menu
 *   getMenuByVendor() — returns the public (available) menu for a vendor
 *   getMenuItem()     — single item lookup with existence validation
 *
 * VendorService.requireVendor() is called to validate vendor existence before
 * any menu operation — fail fast with a 404 before touching menu data.
 *
 * All monetary values use BigDecimal — never float/double for prices.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuItemRepository menuItemRepository;
    private final VendorService      vendorService;

    /**
     * Adds a new menu item to a vendor's menu.
     *
     * @param vendorId UUID of the target vendor
     * @param request  validated MenuItemRequest
     * @return MenuItemResponse for the persisted item
     * @throws ResourceNotFoundException if vendor does not exist
     */
    @Transactional
    public MenuItemResponse addMenuItem(UUID vendorId, MenuItemRequest request) {
        Vendor vendor = vendorService.requireVendor(vendorId);

        MenuItem item = MenuItem.builder()
                .vendor(vendor)
                .name(request.name().trim())
                .description(request.description())
                .price(request.price())
                .category(request.category())
                .available(true)
                .build();

        MenuItem saved = menuItemRepository.save(item);
        log.info("MenuItem added: id={}, name={}, vendorId={}", saved.getId(), saved.getName(), vendorId);
        return MenuItemResponse.from(saved);
    }

    /**
     * Returns the publicly visible menu for a vendor — available items only.
     * Sorted by category then name (repository query handles ordering).
     *
     * @param vendorId UUID of the vendor
     * @return list of available MenuItemResponse
     * @throws ResourceNotFoundException if vendor does not exist
     */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getMenuByVendor(UUID vendorId) {
        // Validate vendor exists before querying menu — gives 404 on unknown vendor
        // rather than an empty list that looks like a valid (but empty) menu.
        vendorService.requireVendor(vendorId);

        return menuItemRepository.findAvailableByVendorId(vendorId)
                .stream()
                .map(MenuItemResponse::from)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the full menu for a vendor including unavailable items.
     * For admin/vendor management views.
     *
     * @param vendorId UUID of the vendor
     * @return all non-deleted MenuItemResponse for the vendor
     */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getFullMenuByVendor(UUID vendorId) {
        vendorService.requireVendor(vendorId);
        return menuItemRepository.findAllByVendorId(vendorId)
                .stream()
                .map(MenuItemResponse::from)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Single menu item lookup — used internally by OrderService and for
     * item detail endpoints.
     *
     * @param itemId UUID of the menu item
     * @return MenuItem entity (for service-to-service calls)
     * @throws ResourceNotFoundException if item does not exist or is deleted
     */
    @Transactional(readOnly = true)
    public MenuItem requireMenuItem(UUID itemId) {
        return menuItemRepository.findActiveById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", itemId));
    }
}