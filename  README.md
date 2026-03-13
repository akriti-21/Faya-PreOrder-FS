# foodorder-backend

Spring Boot 3.2 backend skeleton for a food ordering system.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Security | Spring Security 6 + JJWT 0.12 |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Build | Maven 3.8+ |
| Container | Docker (multi-stage, layered JAR) |

## Quick Start

```bash
# 1. Clone and enter the project
git clone <repo> && cd foodorder-backend

# 2. Copy env template and fill in secrets
make setup   # copies .env.example → .env

# 3. Edit .env — at minimum:
#   DB_PASSWORD=<anything for local dev>
#   JWT_SECRET=$(openssl rand -base64 32)

# 4. Start everything
make up

# 5. Verify
curl http://localhost:8080/api/v1/health
```

## Project Structure

```
src/main/java/com/foodorder/
├── FoodOrderApplication.java    Entry point
├── config/                      Spring configuration classes
│   ├── CorsConfig.java          CORS policy
│   ├── JwtProperties.java       app.jwt.* binding + validation
│   ├── PasswordEncoderConfig.java  BCrypt encoder bean
│   └── SecurityConfig.java      SecurityFilterChain, AuthenticationManager
├── controller/
│   └── HealthController.java    GET /api/v1/health
├── dto/response/
│   ├── ApiResponse.java         Universal response envelope
│   └── ApiError.java            Field-level validation error
├── exception/
│   ├── BusinessException.java   409 Conflict domain errors
│   ├── GlobalExceptionHandler.java  @RestControllerAdvice
│   └── ResourceNotFoundException.java  404 Not Found
├── security/
│   ├── JwtAuthenticationEntryPoint.java  401 handler
│   ├── JwtAuthenticationFilter.java      Per-request JWT validation
│   ├── JwtTokenProvider.java             Token generation + validation
│   └── UserDetailsServiceImpl.java       Stub → wire to UserRepository (Day 2)
└── util/
    └── RequestLoggingFilter.java  MDC traceId + request/response logging
```

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_PASSWORD` | ✅ | — | PostgreSQL password |
| `JWT_SECRET` | ✅ | — | Base64 HMAC secret (≥32 bytes) |
| `DB_USERNAME` | | `foodorder` | PostgreSQL username |
| `DB_URL` | | `jdbc:postgresql://localhost:5432/foodorder` | JDBC URL |
| `JWT_EXPIRATION_MS` | | `3600000` | Access token TTL (1h) |
| `JWT_REFRESH_EXPIRATION_MS` | | `86400000` | Refresh token TTL (24h) |
| `CORS_ALLOWED_ORIGINS` | | `http://localhost:3000,...` | Comma-separated origins |
| `SPRING_PROFILES_ACTIVE` | | `prod` | Spring profile |

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/health` | None | Application liveness |
| GET | `/actuator/health` | None (port 8081) | Deep health + DB check |

## Security Architecture

- **Stateless JWT**: no server-side sessions; every request carries a Bearer token
- **CSRF disabled**: correct for stateless JWT APIs (no session cookies)
- **Per-request user load**: UserDetails loaded fresh on each request for role/revocation consistency
- **Secret validation**: app refuses to start with missing or short JWT secret
- **No secrets in source**: all credentials via environment variables with `${VAR:?error}` syntax

## Day 2 Checklist

- [ ] Implement `UserDetailsServiceImpl` → wire to `UserRepository`
- [ ] Add `User` JPA entity
- [ ] Add `POST /api/v1/auth/login` endpoint
- [ ] Add `POST /api/v1/auth/register` endpoint
- [ ] Add `POST /api/v1/auth/refresh` endpoint
- [ ] Add Redis token revocation blocklist
- [ ] Add `Order`, `Restaurant`, `MenuItem` entities + APIs