# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy jar from builder
COPY --from=builder /app/target/*.jar app.jar

# Expose port
EXPOSE 8083

# Active Spring profile (override with -e SPRING_PROFILES_ACTIVE=recf)
ENV SPRING_PROFILES_ACTIVE=local

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
