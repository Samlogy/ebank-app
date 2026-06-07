# # ── Stage 1: build ────────────────────────────────────────────────────────────
# FROM eclipse-temurin:21-jdk-alpine AS builder

# WORKDIR /build

# # Cache the dependency layer separately from the source layer
# COPY pom.xml .
# RUN --mount=type=cache,target=/root/.m2 \
#     apk add --no-cache maven 2>/dev/null || \
#     (apk add --no-cache curl && \
#      curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz | tar -xz -C /opt && \
#      ln -s /opt/apache-maven-3.9.6/bin/mvn /usr/local/bin/mvn) && \
#     mvn dependency:go-offline -B

# COPY src ./src

# RUN mvn clean package -DskipTests -B

# # ── Stage 2: runtime ───────────────────────────────────────────────────────────
# FROM eclipse-temurin:21-jre-alpine

# # Create a non-root user for the process
# RUN addgroup -S spring && adduser -S spring -G spring

# WORKDIR /app

# # Copy the fat jar produced by the builder stage
# COPY --from=builder /build/target/*.jar app.jar

# # Set ownership so the spring user can read the jar
# RUN chown spring:spring app.jar

# USER spring

# EXPOSE 8080

# ENTRYPOINT ["java", "-jar", "app.jar"]


# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy maven/gradle files
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build
COPY src src
RUN ./mvnw clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy jar from builder
COPY --from=builder /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Run with optimized JVM settings
ENTRYPOINT ["java", "-jar", "app.jar"]