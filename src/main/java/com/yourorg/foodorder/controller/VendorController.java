package com.yourorg.foodorder.controller;

import com.yourorg.foodorder.dto.request.VendorRequest;
import com.yourorg.foodorder.dto.response.ApiResponse;
import com.yourorg.foodorder.dto.response.VendorResponse;
import com.yourorg.foodorder.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * VendorController — vendor catalogue endpoints.
 *
 * POST /api/v1/vendors  — admin creates a vendor (ROLE_ADMIN required)
 * GET  /api/v1/vendors  — public active vendor listing
 * GET  /api/v1/vendors/{id} — public single vendor detail
 *
 * Security note:
 *   GET endpoints are open — browsing vendors does not require login.
 *   POST is restricted to ROLE_ADMIN via @PreAuthorize.
 *   SecurityConfig catches unauthenticated access to protected paths
 *   before @PreAuthorize fires, keeping the 401/403 separation clean.
 */
@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    /**
     * POST /api/v1/vendors
     * Create a new vendor. Admin only.
     *
     * Returns 201 Created with the persisted VendorResponse.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VendorResponse>> createVendor(
            @Valid @RequestBody VendorRequest request) {

        VendorResponse vendor = vendorService.createVendor(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Vendor created successfully.", vendor));
    }

    /**
     * GET /api/v1/vendors
     * List all active vendors. Public.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<VendorResponse>>> listVendors() {
        List<VendorResponse> vendors = vendorService.listVendors();
        return ResponseEntity.ok(
                ApiResponse.success("Vendors retrieved successfully.", vendors));
    }

    /**
     * GET /api/v1/vendors/{id}
     * Get a single vendor by ID. Public.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VendorResponse>> getVendor(
            @PathVariable java.util.UUID id) {

        VendorResponse vendor = vendorService.getVendor(id);
        return ResponseEntity.ok(
                ApiResponse.success("Vendor retrieved successfully.", vendor));
    }
}