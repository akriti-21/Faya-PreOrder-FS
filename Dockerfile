# =============================================================================
# Dockerfile — multi-stage build for Spring Boot 3 / Java 17
#
# Stages
# ──────
#   1. deps     Download Maven dependencies (cached layer — invalidated only
#               when pom.xml changes, not on source changes)
#   2. build    Compile source and package the fat JAR
#   3. extract  Unpack the layered JAR into discrete directories
#   4. runtime  Minimal JRE-only image; copy layers in cache-optimal order
#
# Layered JAR (enabled in pom.xml)
# ─────────────────────────────────
# Spring Boot splits the fat-JAR into four layers ordered by change frequency:
#   dependencies          3rd-party libs       → rarely changes → cached longest
#   spring-boot-loader    Boot loader           → almost never changes
#   snapshot-dependencies SNAPSHOT libs         → changes occasionally
#   application           application code      → changes on every commit
#
# On a code-only change, Docker rebuilds only the top application layer (~2 MB).
# The other three layers (~150 MB) are served from the layer cache.
#
# Base image choice: eclipse-temurin
# ────────────────────────────────────
# Eclipse Temurin is the Eclipse Foundation's production-grade OpenJDK build
# (formerly AdoptOpenJDK). It is TCK-certified, receives regular security patches,
# and is the recommended JDK for containerised Java workloads.
# Alpine variant: ~200 MB vs ~600 MB for debian-slim; suitable for production.
#
# Security
# ────────
#   - JRE-only runtime: no compiler, no javac, no jlink in the final image
#   - Non-root user spring (UID 1001, GID 1001)
#   - No secrets baked into the image; all injected at runtime via env vars
#   - read_only filesystem enforced in docker-compose; tmpfs for /tmp
# =============================================================================

# ─────────────────────────────────────────────────── Stage 1: dependency cache
FROM maven:3.9-eclipse-temurin-17-alpine AS deps

# Why this base image instead of eclipse-temurin + manual Maven install?
# maven:3.9-eclipse-temurin-17-alpine is the official Maven image built on
# Temurin — it includes a verified, pre-installed Maven 3.9 and the same JDK.
# Installing Maven via `apk add maven` pulls the Alpine-packaged version which
# may lag behind the official Maven release schedule.

WORKDIR /build

# Copy only the POM first. Docker invalidates this layer only when pom.xml
# changes — source edits leave the dependency download layer untouched.
COPY pom.xml ./

# Download all declared dependencies to the local repository.
# -B = batch (no interactive prompts, clean CI output)
# The .m2 directory is populated inside the image layer for the next stage.
RUN mvn dependency:go-offline -B

# ────────────────────────────────────────────────────────── Stage 2: build JAR
FROM deps AS build

# Copy Maven wrapper configuration (version pins the Maven wrapper release)
COPY .mvn/ .mvn/

# Copy application source. Separated from pom.xml copy so this layer is only
# invalidated when source changes, not when re-running the same pom.xml.
COPY src/ src/

# Compile and package. Flags:
#   -B           batch mode — no ANSI colours in CI logs
#   -DskipTests  tests run in the CI pipeline, not during image builds
#   -o           offline — use the .m2 cache populated in the deps stage
RUN mvn package -B -DskipTests -o

# ──────────────────────────────────────────────────── Stage 3: extract layers
FROM eclipse-temurin:17-jre-alpine AS extract

WORKDIR /extract

# Copy only the fat JAR from the build stage — nothing else
COPY --from=build /build/target/*.jar app.jar

# jarmode=layertools extracts the layered JAR into subdirectories.
# Each subdirectory becomes a separate COPY in the runtime stage,
# which Docker maps to a separate image layer.
RUN java -Djarmode=layertools -jar app.jar extract

# ──────────────────────────────────────────────────────── Stage 4: runtime
FROM eclipse-temurin:17-jre-alpine AS runtime

# ── Labels ────────────────────────────────────────────────────────────────────
# OCI standard labels for image metadata.
# git-sha and build-date are injected at build time via --build-arg.
ARG GIT_SHA=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="foodorder-backend"
LABEL org.opencontainers.image.description="FoodOrder Spring Boot REST API"
LABEL org.opencontainers.image.base.name="eclipse-temurin:17-jre-alpine"
LABEL org.opencontainers.image.revision="${GIT_SHA}"
LABEL org.opencontainers.image.created="${BUILD_DATE}"

# ── Non-root user ─────────────────────────────────────────────────────────────
# Fixed UID/GID (1001) for predictable file ownership in volume mounts and
# audit logs. Never run production JVM processes as root.
RUN addgroup -S spring -g 1001 \
 && adduser  -S spring -u 1001 -G spring

WORKDIR /app

# ── Layered JAR — copy in order of change frequency (least → most) ────────────
# Each COPY creates a Docker layer. Ordering by change frequency means
# the layers most likely to be cache-hit (dependencies, loader) come first.
COPY --from=extract --chown=spring:spring /extract/dependencies/           ./
COPY --from=extract --chown=spring:spring /extract/spring-boot-loader/     ./
COPY --from=extract --chown=spring:spring /extract/snapshot-dependencies/  ./
COPY --from=extract --chown=spring:spring /extract/application/            ./

USER spring:spring

# ── Port ──────────────────────────────────────────────────────────────────────
# Expose API port. Management port (8081) stays internal and is not published
# to the host — only accessible within the Docker network.
EXPOSE 8080

# ── JVM tuning ────────────────────────────────────────────────────────────────
# -XX:+UseContainerSupport  (default in JDK 11+)
#   Reads cgroup memory/CPU limits instead of host hardware values.
#   Without this, the JVM calculates heap based on host RAM and may be
#   OOMKilled when the container limit is much lower than host RAM.
#
# -XX:MaxRAMPercentage=75.0
#   Allow heap up to 75% of the container memory limit.
#   The remaining 25% covers: Metaspace, thread stacks, JIT compiled code,
#   GC bookkeeping, and OS buffers. Adjust for memory-intensive workloads.
#
# -XX:+ExitOnOutOfMemoryError
#   Crash immediately on OOM instead of limping with degraded performance.
#   Docker / Kubernetes will restart the container. A healthy restart is
#   always better than a half-dead JVM serving partial responses.
#
# -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof
#   Write a heap dump to /tmp on OOM. Combined with docker cp or a volume
#   mount on /tmp, this enables post-mortem analysis without ssh access.
#   /tmp is a tmpfs in docker-compose — heapdump survives the container restart.
#
# -Djava.security.egd=file:/dev/./urandom
#   Use /dev/urandom instead of /dev/random for SecureRandom seeding.
#   Prevents startup delays in containers with low entropy pools.
#   The /dev/./ path is a workaround for a JDK bug that redirected
#   /dev/urandom to /dev/random on some Linux kernels; included for safety.
#
# -Dfile.encoding=UTF-8
#   Explicit UTF-8 regardless of the container's LANG/LC_ALL locale.
#   Alpine containers often default to POSIX locale (US-ASCII), which
#   causes garbled characters when reading UTF-8 configuration files.
#
# -Dspring.profiles.active
#   Resolved at container startup from SPRING_PROFILES_ACTIVE env var.
#   Defaults to 'prod' if not set — safe default for production deployments.
ENTRYPOINT ["java",                                                    \
    "-XX:+UseContainerSupport",                                        \
    "-XX:MaxRAMPercentage=75.0",                                       \
    "-XX:+ExitOnOutOfMemoryError",                                     \
    "-XX:+HeapDumpOnOutOfMemoryError",                                 \
    "-XX:HeapDumpPath=/tmp/heapdump.hprof",                            \
    "-Djava.security.egd=file:/dev/./urandom",                         \
    "-Dfile.encoding=UTF-8",                                           \
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}",        \
    "org.springframework.boot.loader.launch.JarLauncher"]

# ── Health check ──────────────────────────────────────────────────────────────
# Docker uses this to determine container health status.
# docker-compose depends_on: condition: service_healthy waits for this.
# wget is available in eclipse-temurin alpine images.
# --start-period=45s accounts for JVM startup + Flyway migration time.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/v1/health || exit 1
   # ═══════════════════════════════════════════════════════════════════════════════
# Hardened multi-stage Dockerfile — Day 10
# Does NOT modify any application source code.
# ═══════════════════════════════════════════════════════════════════════════════

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Cache dependency layer
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Build application
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Extract layered jar for optimized Docker layers
RUN java -Djarmode=layertools \
         -jar target/*.jar extract --destination /build/layers

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# ── Security: non-root user ───────────────────────────────────────────────────
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy layered jar content in dependency-cache-friendly order
COPY --from=builder --chown=appuser:appgroup /build/layers/dependencies/          ./
COPY --from=builder --chown=appuser:appgroup /build/layers/spring-boot-loader/    ./
COPY --from=builder --chown=appuser:appgroup /build/layers/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/layers/application/           ./

USER appuser

# ── JVM memory limits ─────────────────────────────────────────────────────────
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+UseG1GC \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/tmp/heapdump.hprof \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:production}"

EXPOSE 8080

# ── Health check ─────────────────────────────────────────────────────────────
HEALTHCHECK --interval=30s \
            --timeout=5s \
            --start-period=60s \
            --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.JarLauncher"] 