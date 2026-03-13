package com.yourorg.foodorder.controller;

import com.yourorg.foodorder.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Application-level health endpoint at /api/v1/health.
 *
 * Distinct from /actuator/health (management port 8081):
 *
 * /api/v1/health (this endpoint, port 8080):
 *   - Liveness signal: "is the JVM running and serving requests?"
 *   - No auth required (public)
 *   - Suitable for load balancer health probes on the API port
 *   - Does NOT check database or external dependencies
 *
 * /actuator/health (port 8081):
 *   - Readiness/liveness: checks DB pool, disk space, external services
 *   - Managed separately; can be restricted to internal networks
 *   - Use for Kubernetes readiness/liveness probes
 *   - Use for deep health monitoring dashboards
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        Map<String, String> data = Map.of(
                "status",    "UP",
                "timestamp", Instant.now().toString(),
                "service",   "foodorder-backend"
        );
        return ResponseEntity.ok(ApiResponse.success("Service is running", data));
    }
}