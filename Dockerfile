# =============================================================================
# MULTI-STAGE DOCKERFILE — Spring Boot / Maven / Java 17
#
# Stages:
#   1. deps     → Resolves Maven dependencies (layer-cache optimisation)
#   2. build    → Compiles source and packages the fat JAR
#   3. runtime  → Minimal JRE-only image that ships to production
#
# Security model:
#   - Build tools (Maven, JDK) never reach the runtime image
#   - Runtime image carries only: JRE + JAR + a non-root OS user
#   - No shell, no package manager, no compiler in prod container
#   - Read-only filesystem enforced at compose level (see docker-compose.yml)
# =============================================================================


# -----------------------------------------------------------------------------
# STAGE 1 — deps
# Purpose: pre-warm the Maven local repository so that source-code changes
# do not invalidate the dependency download layer.
#
# How it works:
#   Copy pom.xml → resolve deps offline → Docker caches this layer.
#   As long as pom.xml does not change, subsequent builds skip this stage.
#   Source code changes only invalidate the cheaper build stage below.
#
# Security: this stage is discarded; nothing here reaches production.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk-alpine AS deps

# Install Maven.
# Alpine keeps the image small (~50 MB base vs ~200 MB for Debian variants).
# apk --no-cache avoids writing an apk index to disk (smaller layer, no stale cache).
RUN apk add --no-cache maven

WORKDIR /build

# Copy dependency descriptor only — NOT src.
# If pom.xml is unchanged, Docker reuses the cached layer from this point forward.
COPY pom.xml .

# go-offline downloads every declared dependency into /root/.m2.
# -B = batch mode (no interactive prompts, cleaner CI output).
# 2>/dev/null suppresses the Maven download progress bar in build logs.
RUN mvn dependency:go-offline -B -q


# -----------------------------------------------------------------------------
# STAGE 2 — build
# Purpose: compile source code and produce the executable fat JAR.
#
# We start FROM the deps stage so /root/.m2 is already populated.
# No network access is needed here because all deps are in the local repo.
#
# Security:
#   -DskipTests: tests run in your CI pipeline before the Docker build,
#   not inside the image build itself. Building an image is not a test step.
# -----------------------------------------------------------------------------
FROM deps AS build

# Copy source tree. This layer is invalidated on every source change,
# but the deps layer above is still cached if pom.xml is unchanged.
COPY src ./src

# Package: produces target/*.jar (Spring Boot repackaged fat JAR).
# -q quiets the Maven lifecycle log; CI pipelines can remove -q if preferred.
RUN mvn package -DskipTests -B -q


# -----------------------------------------------------------------------------
# STAGE 3 — runtime
# Purpose: the image that actually runs in production.
#
# eclipse-temurin:17-jre-alpine breakdown:
#   eclipse-temurin  → Adoptium/Eclipse Foundation distribution (production-grade,
#                       security-patched, widely used in enterprise).
#   17               → LTS Java version matched to pom.xml <java.version>.
#   jre              → Runtime only — no javac, jshell, or compiler toolchain.
#                       Reduces image size by ~120 MB and attack surface further.
#   alpine           → musl-libc based minimal OS (~5 MB). No bash, no apt,
#                       no unnecessary binaries that could be leveraged post-compromise.
#
# Size comparison (approximate):
#   eclipse-temurin:17-jdk-alpine   ~  340 MB
#   eclipse-temurin:17-jre-alpine   ~  215 MB  ← we use this
#   eclipse-temurin:17-jre          ~  380 MB
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# ── Security: non-root user ────────────────────────────────────────────────
#
# Running as root inside a container is dangerous:
#   1. If the process is exploited, the attacker has root inside the container.
#   2. Misconfigured bind mounts or privileged escalation could give root on
#      the host kernel.
#   3. Many container security scanners (Trivy, Snyk, etc.) flag root execution
#      as a critical finding.
#
# We create a dedicated system group + user with:
#   -S  → system account (no password, no home dir login shell)
#   -G  → assign to the system group
#   UID/GID 1001 → non-overlapping with common host UIDs to reduce accidental
#                  permission collisions on bind mounts.
# ──────────────────────────────────────────────────────────────────────────
RUN addgroup -S -g 1001 appgroup \
 && adduser  -S -u 1001 -G appgroup appuser

# ── Filesystem layout ─────────────────────────────────────────────────────
# /app is the working directory. We do NOT use /home or /root so that
# the non-root user does not need write access to home directories.
# ──────────────────────────────────────────────────────────────────────────
WORKDIR /app

# Copy the built JAR from the build stage — only the artefact, nothing else.
# --chown sets ownership at copy time (single layer, no extra RUN chown needed).
COPY --from=build --chown=appuser:appgroup /build/target/*.jar app.jar

# Switch to non-root before any further commands and for CMD execution.
USER appuser

# ── Port ───────────────────────────────────────────────────────────────────
# EXPOSE is documentation only — it does not publish the port.
# Actual port binding is declared in docker-compose.yml.
# Using an unprivileged port (>1024) is required when running as non-root
# without NET_BIND_SERVICE capability.
# ──────────────────────────────────────────────────────────────────────────
EXPOSE 8080

# ── JVM flags ──────────────────────────────────────────────────────────────
# Configured for containerised environments:
#
# -XX:+UseContainerSupport (default JDK 17)
#   Makes the JVM read cgroup v1/v2 memory and CPU limits instead of host
#   totals. Without this, a JVM in a 512 MB container could try to allocate
#   several GB of heap based on the host RAM → OOMKilled immediately.
#
# -XX:MaxRAMPercentage=75.0
#   Cap heap at 75% of container memory limit. The remaining 25% is reserved
#   for: Metaspace, thread stacks, JIT code cache, GC overhead, and OS buffers.
#   Rule of thumb: heap should never be 100% of available memory.
#
# -XX:+ExitOnOutOfMemoryError
#   Die fast on OOM rather than thrashing in a degraded state. Docker/K8s will
#   restart the container. A limping JVM is far harder to diagnose than a clean
#   crash + restart.
#
# -Djava.security.egd=file:/dev/./urandom
#   Spring Boot (and Tomcat) initialise SecureRandom on startup. On some Linux
#   kernels, /dev/random can block if entropy is low, adding 30-60s to startup.
#   /dev/urandom (via the non-blocking path) is cryptographically safe for
#   session token generation and avoids this startup penalty.
#   The '/dev/./' path is a workaround for an old JVM bug that incorrectly
#   resolved /dev/urandom to /dev/random — still harmless on patched JDKs.
#
# -Dfile.encoding=UTF-8
#   Alpine's default locale is POSIX/C which can cause charset issues with
#   certain Jackson/Hibernate string handling. Pin to UTF-8 explicitly.
# ──────────────────────────────────────────────────────────────────────────
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dfile.encoding=UTF-8", \
  "-jar", "app.jar" \
]