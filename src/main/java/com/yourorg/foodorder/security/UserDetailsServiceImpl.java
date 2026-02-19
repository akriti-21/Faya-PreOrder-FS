package com.foodorder.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * UserDetailsService implementation — loads user data for Spring Security.
 *
 * This is a SKELETON implementation for Day 1 infrastructure setup.
 * It will be replaced when the User domain entity and UserRepository are implemented.
 *
 * TODO (Day 2): Replace this implementation with:
 *   1. Inject UserRepository
 *   2. Query: userRepository.findByEmail(username)
 *              .orElseThrow(() -> new UsernameNotFoundException(...))
 *   3. Return a SecurityPrincipal wrapping the User domain entity
 *
 * Current behavior: throws UsernameNotFoundException for all users.
 * This is intentional — authentication will fail gracefully until real
 * user data is wired in. The security infrastructure is fully functional.
 *
 * Architecture note: UserDetailsService is called by:
 *   - JwtAuthenticationFilter (on every authenticated request)
 *   - AuthenticationManager during login (via DaoAuthenticationProvider)
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // TODO: Replace with real DB lookup
        // User user = userRepository.findByEmail(username)
        //     .orElseThrow(() -> new UsernameNotFoundException(
        //         "User not found with email: " + username));
        // return SecurityPrincipal.of(user);

        throw new UsernameNotFoundException(
                "User not found: " + username +
                " [UserDetailsServiceImpl is not yet wired to a real datasource]"
        );
    }
}