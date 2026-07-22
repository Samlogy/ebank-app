#FROM maven:3.8.8-eclipse-temurin-17-alpine AS build
#WORKDIR /app
#COPY pom.xml ./
#COPY src ./src
#RUN mvn clean install -DskipTests

#FROM eclipse-temurin:17-jre-alpine
#WORKDIR /app
##COPY --from=build /app/target/product-1.0.0.jar app.jar
#COPY /target/product-1.0.0.jar app.jar
#EXPOSE 4002
#CMD ["java", "-jar", "app.jar"]


# *** 21 version
# Use Maven image with JDK 21 for the build stage
#FROM maven:3.9.6-eclipse-temurin-21 AS build
#
#WORKDIR /app
#
## Copy only the pom.xml and run dependency download first (cache optimization)
#COPY pom.xml ./
#RUN mvn dependency:go-offline
#
## Copy the source code
#COPY src ./src
#
## Build the application
#RUN mvn clean install -DskipTests
#
## Use Amazon Corretto for the runtime stage
#FROM amazoncorretto:21
#WORKDIR /app
#
## Copy the built JAR from the build stage
#    COPY --from=build /app/target/accounts-*.jar app.jar
#
## Expose the port and run the application
#EXPOSE 8082
#CMD ["java", "-jar", "app.jar"]






# Build stage
# Use the Maven base image and `mvn` directly: the Maven wrapper is not usable
# here because .mvn/wrapper/maven-wrapper.properties is gitignored repo-wide
# (see .gitignore), so `./mvnw` cannot resolve a distribution to download.
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy pom and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
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
EXPOSE 8082

# Run with optimized JVM settings
ENTRYPOINT ["java", "-jar", "app.jar"]