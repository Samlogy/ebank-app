# ── Stage 1: Resolve dependencies (cached as long as pom.xml unchanged) ──────
FROM maven:3.9.6-eclipse-temurin-17 AS deps
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline -q

# ── Stage 2: Build the JAR ────────────────────────────────────────────────────
FROM deps AS builder
COPY src ./src
RUN mvn -B clean package -DskipTests -q

# ── Stage 3: Minimal runtime image ───────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S ebank && adduser -S ebank -G ebank
WORKDIR /app
COPY --from=builder /app/target/ebank-monolith-*.jar app.jar
RUN chown ebank:ebank app.jar
USER ebank
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
