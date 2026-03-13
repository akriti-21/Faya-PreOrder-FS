package com.yourorg.foodorder.service;

import com.yourorg.foodorder.domain.Vendor;
import com.yourorg.foodorder.dto.request.VendorRequest;
import com.yourorg.foodorder.dto.response.VendorResponse;
import com.yourorg.foodorder.exception.ResourceNotFoundException;
import com.yourorg.foodorder.repository.VendorRepository;
import com.yourorg.foodorder.security.SecurityPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * VendorService — vendor lifecycle management.
 *
 * Responsibilities:
 *   createVendor() — admin creates a new vendor; owner defaults to the
 *                    calling admin's user ID unless overridden.
 *   listVendors()  — returns active, non-deleted vendors for public browsing.
 *   getVendor()    — single vendor lookup with existence validation.
 *
 * Authorization:
 *   createVendor() is restricted to ROLE_ADMIN at the controller layer
 *   (@PreAuthorize). The service trusts this and does not re-check the role.
 *   This keeps security concerns in one layer and the service testable without
 *   a Spring Security context.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorService {

    private final VendorRepository vendorRepository;

    /**
     * Creates a new vendor. The ownerId is set to the authenticated admin's
     * user ID — the admin who creates the vendor implicitly becomes the owner.
     * Ownership can be reassigned via a separate admin endpoint (Day N).
     *
     * @param request validated VendorRequest
     * @return VendorResponse for the persisted entity
     */
    @Transactional
    public VendorResponse createVendor(VendorRequest request) {
        UUID ownerId = currentUserId();

        Vendor vendor = Vendor.builder()
                .ownerId(ownerId)
                .name(request.name().trim())
                .description(request.description())
                .cuisineType(request.cuisineType())
                .phone(request.phone())
                .address(request.address())
                .active(true)
                .build();

        Vendor saved = vendorRepository.save(vendor);
        log.info("Vendor created: id={}, name={}, ownerId={}", saved.getId(), saved.getName(), ownerId);
        return VendorResponse.from(saved);
    }

    /**
     * Returns all active, non-deleted vendors sorted by name.
     * Public endpoint — no auth required (enforced in SecurityConfig).
     */
    @Transactional(readOnly = true)
    public List<VendorResponse> listVendors() {
        return vendorRepository.findAllActiveVendors()
                .stream()
                .map(VendorResponse::from)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Retrieves a single active vendor by ID.
     * Called by MenuService and OrderService to validate vendor existence.
     *
     * @param vendorId UUID of the vendor
     * @return VendorResponse
     * @throws ResourceNotFoundException if vendor does not exist or is deleted
     */
    @Transactional(readOnly = true)
    public VendorResponse getVendor(UUID vendorId) {
        Vendor vendor = vendorRepository.findActiveById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", "id", vendorId));
        return VendorResponse.from(vendor);
    }

    /**
     * Internal lookup returning the Vendor entity (not DTO).
     * Used by MenuService and OrderService which need the JPA entity
     * to set as a FK reference on MenuItem and Order.
     */
    @Transactional(readOnly = true)
    public Vendor requireVendor(UUID vendorId) {
        return vendorRepository.findActiveById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", "id", vendorId));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SecurityPrincipal principal = (SecurityPrincipal) auth.getPrincipal();
        return principal.getUserId();
    }
}