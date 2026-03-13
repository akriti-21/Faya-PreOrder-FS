package com.yourorg.foodorder.dto.response;

import com.yourorg.foodorder.domain.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Outbound representation of a User — safe to serialise in API responses.
 *
 * Explicitly excludes:
 *   - password / password hash (never exposed)
 *   - deletedAt (internal housekeeping)
 *   - Hibernate internal state
 *
 * Static factory from(User) keeps the mapping co-located with the DTO
 * rather than scattered across service/controller layers. If the mapping
 * grows complex, extract to a dedicated UserMapper (MapStruct).
 *
 * roles is a Set<String> of role names (e.g. ["ROLE_USER"]) — simpler for
 * clients than a list of role objects with UUID and name.
 */
public record UserResponse(
    UUID      id,
    String    email,
    String    firstName,
    String    lastName,
    boolean   enabled,
    Set<String> roles,
    Instant   createdAt,
    Instant   updatedAt
) {

    /**
     * Maps a User entity to UserResponse.
     * Called after registration and login — never passes password to the output.
     */
    public static UserResponse from(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toUnmodifiableSet());

        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.isEnabled(),
            roleNames,
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}