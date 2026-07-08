# ═══════════════════════════════════════════════════════════════════════════════
# Phase 15 — Dockerfile (Multi-Stage Build)
#
# WHY MULTI-STAGE?
#   Stage 1 (build): needs JDK + Maven + all source files = ~700MB image
#   Stage 2 (run):   needs JRE + compiled JAR only = ~250MB image
#
#   Without multi-stage: final image contains Maven, source code, compile tools
#   → larger image → slower pulls, more attack surface, bigger storage cost
#
#   With multi-stage: COPY --from=build copies ONLY the JAR
#   The build tools never end up in the final image.
#
# LAYERS (order matters for caching):
#   Docker builds images in layers. Each instruction creates a layer.
#   If a layer hasn't changed, Docker reuses the cached version.
#
#   We copy pom.xml and download deps BEFORE copying src.
#   Why? Source code changes frequently. Dependencies rarely change.
#   If only src/ changes → Docker reuses the cached "download deps" layer
#   → build is fast (seconds, not minutes on repeat builds).
#
#   If we copied everything at once (COPY . .) → any change to any file
#   invalidates the deps layer → full re-download every build.
#
# eclipse-temurin:17-jdk-alpine:
#   eclipse-temurin = Eclipse Temurin OpenJDK (formerly AdoptOpenJDK)
#   alpine = minimal Linux (~5MB base vs ~70MB for Ubuntu)
#   17 = Java 17 LTS
#   JDK (Java Development Kit) = needed to compile (stage 1)
#   JRE (Java Runtime Environment) = needed to run only (stage 2)
#
# /dev/./urandom:
#   By default, Java's SecureRandom blocks waiting for OS entropy.
#   In containers, entropy sources are limited → JVM startup hangs for 30s+.
#   /dev/urandom is non-blocking (safe for most applications).
#   The /./  is a JVM workaround — it forces urandom even on some JVMs
#   that normalize the path and then use /dev/random.
# ═══════════════════════════════════════════════════════════════════════════════

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copy pom.xml first — download dependencies as a separate cacheable layer
# Docker cache: if pom.xml hasn't changed, skip re-downloading (fast builds)
COPY pom.xml .
RUN mvn dependency:go-offline --no-transfer-progress

# Now copy source and compile
# This layer changes on every code change, but dep layer above is still cached
COPY src ./src
RUN mvn package -DskipTests --no-transfer-progress

# ── Stage 2: Run ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

# Create non-root user — security best practice
# Running as root in a container is dangerous: if the app is compromised,
# the attacker has root access to everything the container can reach.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Create uploads directory with correct permissions
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

# Copy ONLY the JAR from the build stage — no source, no Maven, no JDK
COPY --from=build --chown=appuser:appgroup /app/target/ecommerce-backend-*.jar app.jar

# Switch to non-root user before starting the app
USER appuser

# Document which port the container listens on (doesn't actually publish it)
EXPOSE 8080

# Health check: Docker periodically runs this to know if the container is healthy
# If it fails 3 times → container marked "unhealthy"
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/actuator/health || exit 1

# JVM tuning for containers:
#   -Djava.security.egd=file:/dev/./urandom → fast startup (see above)
#   -XX:+UseContainerSupport → JVM reads container's CPU/RAM limits (not host's)
#   -XX:MaxRAMPercentage=75.0 → use up to 75% of container's RAM for heap
#   Without container support: JVM sees host's 32GB RAM, sets heap to 8GB,
#   but container has 512MB limit → OOMKilled immediately
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}", \
    "-jar", "app.jar"]
