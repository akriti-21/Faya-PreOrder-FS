package com.yourorg.foodorder.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Custom health indicator for PostgreSQL connectivity.
 * Exposed at /actuator/health/database (auto-named by Spring Boot).
 * Returns UP with latency detail, or DOWN with error message.
 */
@Component("database")
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthIndicator.class);
    private static final String VALIDATION_QUERY = "SELECT 1";
    private static final int    TIMEOUT_SECONDS  = 3;

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        long startMs = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            conn.setNetworkTimeout(Runnable::run, TIMEOUT_SECONDS * 1_000);

            try (PreparedStatement ps = conn.prepareStatement(VALIDATION_QUERY);
                 ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    long latencyMs = System.currentTimeMillis() - startMs;
                    return Health.up()
                            .withDetail("database",   "PostgreSQL")
                            .withDetail("latencyMs",  latencyMs)
                            .withDetail("validation", VALIDATION_QUERY)
                            .build();
                }
                return Health.down()
                        .withDetail("reason", "Validation query returned no rows")
                        .build();
            }
        } catch (Exception ex) {
            log.error("Database health check failed", ex);
            return Health.down(ex)
                    .withDetail("reason", ex.getMessage())
                    .build();
        }
    }
}