package com.foodorder.controller;

import com.foodorder.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Application health check controller.
 *
 * Architecture note:
 * This is a lightweight custom health endpoint at /api/v1/health.
 * It is separate from Spring Actuator's /actuator/health which provides
 * detailed component health (DB connection, disk space, etc.) and is
 * served on the management port (8081).
 *
 * This endpoint:
 *   - Lives on the application port (8080) — accessible via the same
 *     network path as the API
 *   - Is publicly accessible without authentication (see SecurityConfig)
 *   - Returns a minimal "alive" signal suitable for load balancer probes
 *     and basic uptime monitoring
 *   - Does NOT perform DB health checks (use Actuator for that)
 *
 * Use /actuator/health for:
 *   - Deep health checks (DB, cache, external services)
 *   - Kubernetes readiness/liveness probes (on management port)
 *   - Detailed component status
 *
 * Use /api/v1/health for:
 *   - Load balancer health probes on the API port
 *   - Quick "is the app running?" check
 *   - Integration test baseline verification
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    /**
     * Simple liveness check.
     * Returns 200 OK with timestamp if the application context is running.
     * Does not check database or external dependencies.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        Map<String, String> healthData = Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString(),
                "service", "foodorder-backend"
        );

        return ResponseEntity.ok(
                ApiResponse.success("Service is running", healthData)
        );
    }
}