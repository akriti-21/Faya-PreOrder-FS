# Food Ordering System — Backend API

Spring Boot 3 · Java 17 · PostgreSQL · JWT · Docker

---

## Quick Start

```bash
# 1. Clone and enter the project
cd foodorder

# 2. Copy environment template and fill in values
cp .env.example .env
# Edit .env — generate JWT_SECRET: openssl rand -base64 32

# 3. Start the stack
docker compose up --build

# 4. Verify health
curl http://localhost:8080/api/v1/health
curl http://localhost:8081/actuator/health
```

---

## Project Structure

```
src/main/java/com/foodorder/
├── FoodOrderApplication.java       # Entry point
├── config/                         # Spring configuration beans
│   ├── CorsConfig.java             # CORS policy
│   ├── JwtProperties.java          # JWT config binding + validation
│   ├── PasswordEncoderConfig.java  # BCrypt encoder (strength 12)
│   └── SecurityConfig.java         # Security filter chain
├── controller/                     # HTTP layer only
│   └── HealthController.java
├── dto/response/                   # API contracts
│   ├── ApiResponse.java            # Universal response envelope
│   └── ApiError.java              # Field-level validation errors
├── exception/                      # Domain exceptions + handler
│   ├── GlobalExceptionHandler.java # @RestControllerAdvice
│   ├── ResourceNotFoundException.java
│   └── BusinessException.java
├── security/                       # JWT infrastructure
│   ├── JwtAuthenticationEntryPoint.java
│   ├── JwtAuthenticationFilter.java
│   ├── JwtTokenProvider.java
│   └── UserDetailsServiceImpl.java  # TODO: wire to UserRepository
└── util/
    └── RequestLoggingFilter.java   # MDC trace ID + request logging
```

---

## Environment Variables

See `.env.example` for all required variables.

| Variable | Required | Description |
|---|---|---|
| `JWT_SECRET` | **YES** | Base64 secret, min 32 chars. `openssl rand -base64 32` |
| `POSTGRES_PASSWORD` | **YES** | Database password |
| `DB_URL` | prod only | Full JDBC URL |
| `DB_USERNAME` | prod only | DB user |
| `DB_PASSWORD` | prod only | DB password |
| `CORS_ALLOWED_ORIGINS` | no | Comma-separated origins |

---

## Profiles

| Profile | Usage | DDL | SQL Logging |
|---|---|---|---|
| `dev` | Local Docker Compose | validate | ON |
| `test` | CI / Testcontainers | validate | OFF |
| `prod` | Production | validate | OFF |

```bash
# Run with a specific profile
SPRING_PROFILES_ACTIVE=dev java -jar app.jar
```

---

## API Response Contract

Every response uses the same envelope:

```json
{
  "success": true,
  "statusCode": 200,
  "message": "...",
  "data": {},
  "errors": null,
  "timestamp": "2025-01-01T12:00:00Z",
  "traceId": "abc-123"
}
```

---

## Security Baseline

- **Stateless JWT**: No server-side sessions
- **CSRF disabled**: Correct for stateless API (no session cookies)
- **BCrypt strength 12**: Password hashing
- **Secrets via env vars**: Never hardcoded
- **Generic error messages**: No internal details leaked to clients
- **Trace IDs**: Correlation via `X-Trace-Id` header + MDC

---

## Day 2 Checklist

- [ ] Implement `User` entity + `UserRepository`
- [ ] Wire `UserDetailsServiceImpl` to real DB
- [ ] Implement `AuthController` (login/register/refresh)
- [ ] Add token revocation (Redis blocklist in `JwtTokenProvider`)
- [ ] Add `V1__init_schema.sql` with real table definitions
- [ ] Add integration tests with Testcontainers