# ── Build stage ───────────────────────────────────────────────────────────────
# Use the Maven image directly so the build does not depend on the committed
# wrapper (.mvn/maven-wrapper.properties is gitignored repo-wide).
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Dependencies first (cached layer)
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy source and build the fat jar (tests run in CI, not in the image build)
COPY src src
RUN mvn -B clean package -DskipTests

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 3001

ENTRYPOINT ["java", "-jar", "app.jar"]
