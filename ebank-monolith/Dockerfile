FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY ebank-core/pom.xml ebank-core/
COPY ebank-auth/pom.xml ebank-auth/
COPY ebank-accounts/pom.xml ebank-accounts/
COPY ebank-transactions/pom.xml ebank-transactions/
COPY ebank-notifications/pom.xml ebank-notifications/
COPY ebank-app/pom.xml ebank-app/
RUN mvn dependency:go-offline -B

COPY ebank-core/src ebank-core/src
COPY ebank-auth/src ebank-auth/src
COPY ebank-accounts/src ebank-accounts/src
COPY ebank-transactions/src ebank-transactions/src
COPY ebank-notifications/src ebank-notifications/src
COPY ebank-app/src ebank-app/src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /build/ebank-app/target/ebank-app-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
