#!/bin/sh
# =============================================================================
# VAULT INIT — eBank
#
# STRUCTURE VAULT:
#   secret/ebank/
#     auth-service/
#       docker   → config complète auth en docker
#       recf     → config complète auth en recf
#       prod     → config complète auth en prod
#     accounts-service/
#       docker | recf | prod
#     transaction-service/
#       docker | recf | prod
#     gateway/
#       docker | recf | prod
#     notification-service/
#       docker | recf | prod
#
# Les services lisent depuis secret/ebank/{service}/{profile} via:
#   spring.cloud.vault.kv.application-name=ebank/{service}
#   spring.cloud.vault.kv.profile-separator=/
#
# USAGE: sh vault-init.sh [docker|recf|prod]   (défaut: docker)
# =============================================================================

set -eu

ENV="${1:-docker}"
VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-root}"
export VAULT_ADDR VAULT_TOKEN

LOG_PATTERN="%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %X{traceId:-} - %msg%n"
LOG_PATTERN_RECF="%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %X{traceId:-no-trace} - %msg%n"

echo "==> Enable KV v2 at secret/ (idempotent)"
vault secrets enable -version=2 -path=secret kv 2>/dev/null || echo "    already enabled"
echo ""

# =============================================================================
# AUTH SERVICE — secret/ebank/auth-service/{env}
# =============================================================================
case "$ENV" in
  docker)
    echo "[auth-service] docker"
    vault kv put secret/ebank/auth-service/docker \
      "server.port=8081" \
      "spring.datasource.url=jdbc:postgresql://postgres:5432/auth_db" \
      "spring.datasource.username=postgres" \
      "spring.datasource.password=$POSTGRES_PASSWORD" \
      "spring.datasource.driver-class-name=org.postgresql.Driver" \
      "spring.jpa.hibernate.ddl-auto=none" \
      "spring.jpa.show-sql=false" \
      "spring.flyway.enabled=true" \
      "spring.flyway.locations=classpath:db/migration" \
      "spring.flyway.connect-retries=5" \
      "jwt.secret=$JWT_SECRET" \
      "jwt.expiration=900000" \
      "jwt.refresh-expiration=604800000" \
      "spring.data.redis.host=redis" \
      "spring.data.redis.port=6379" \
      "spring.data.redis.password=$REDIS_PASSWORD" \
      "springdoc.api-docs.path=/auth-api-docs" \
      "springdoc.swagger-ui.path=/auth-api-ui" \
      "management.endpoints.web.exposure.include=health,info,metrics,refresh,env" \
      "management.endpoint.health.show-details=ALWAYS" \
      "logging.pattern.console=$LOG_PATTERN" \
      "logging.level.root=INFO" \
      "logging.level.com.ebank.auth=DEBUG" \
      "logging.level.org.springframework.security=INFO" \
      "logging.level.org.hibernate.SQL=DEBUG"
    ;;
  recf)
    echo "[auth-service] recf"
    vault kv put secret/ebank/auth-service/recf \
      "server.port=8081" \
      "spring.datasource.url=${SPRING_DATASOURCE_URL_RECF:?required}" \
      "spring.datasource.username=${SPRING_DATASOURCE_USERNAME_RECF:?required}" \
      "spring.datasource.password=${AUTH_DB_PASSWORD_RECF:?required}" \
      "spring.datasource.driver-class-name=org.postgresql.Driver" \
      "spring.jpa.hibernate.ddl-auto=none" \
      "spring.jpa.show-sql=false" \
      "spring.flyway.enabled=true" \
      "spring.flyway.locations=classpath:db/migration" \
      "spring.flyway.connect-retries=5" \
      "jwt.secret=${JWT_SECRET_RECF:?required}" \
      "jwt.expiration=900000" \
      "jwt.refresh-expiration=604800000" \
      "spring.data.redis.host=${REDIS_HOST_RECF:?required}" \
      "spring.data.redis.port=6379" \
      "spring.data.redis.password=${REDIS_PASSWORD_RECF:?required}" \
      "management.endpoints.web.exposure.include=health,info,metrics,refresh" \
      "management.endpoint.health.show-details=WHEN_AUTHORIZED" \
      "logging.pattern.console=$LOG_PATTERN_RECF" \
      "logging.level.root=INFO" \
      "logging.level.com.ebank.auth=INFO" \
      "logging.level.org.springframework.security=WARN"
    ;;
  prod)
    echo "[auth-service] prod"
    vault kv put secret/ebank/auth-service/prod \
      "server.port=8081" \
      "spring.datasource.url=${SPRING_DATASOURCE_URL_PROD:?required}" \
      "spring.datasource.username=${SPRING_DATASOURCE_USERNAME_PROD:?required}" \
      "spring.datasource.password=${AUTH_DB_PASSWORD_PROD:?required}" \
      "spring.datasource.driver-class-name=org.postgresql.Driver" \
      "spring.jpa.hibernate.ddl-auto=none" \
      "spring.jpa.show-sql=false" \
      "spring.flyway.enabled=true" \
      "spring.flyway.locations=classpath:db/migration" \
      "spring.flyway.connect-retries=5" \
      "jwt.secret=${JWT_SECRET_PROD:?required}" \
      "jwt.expiration=900000" \
      "jwt.refresh-expiration=604800000" \
      "spring.data.redis.host=${REDIS_HOST_PROD:?required}" \
      "spring.data.redis.port=6379" \
      "spring.data.redis.password=${REDIS_PASSWORD_PROD:?required}" \
      "management.endpoints.web.exposure.include=health,info" \
      "management.endpoint.health.show-details=NEVER" \
      "logging.pattern.console=$LOG_PATTERN" \
      "logging.level.root=WARN" \
      "logging.level.com.ebank.auth=WARN" \
      "logging.level.org.springframework.security=WARN"
    ;;
esac

# =============================================================================
# ACCOUNTS SERVICE — secret/ebank/accounts-service/{env}
# =============================================================================
case "$ENV" in
  docker)
    echo "[accounts-service] docker"
    vault kv put secret/ebank/accounts-service/docker \
      "server.port=8082" \
      "spring.r2dbc.url=r2dbc:postgresql://postgres:5432/accounts_db" \
      "spring.r2dbc.username=postgres" \
      "spring.r2dbc.password=$POSTGRES_PASSWORD" \
      "spring.flyway.url=jdbc:postgresql://postgres:5432/accounts_db" \
      "spring.flyway.user=postgres" \
      "spring.flyway.password=$POSTGRES_PASSWORD" \
      "spring.flyway.enabled=true" \
      "spring.flyway.locations=classpath:db/migration" \
      "spring.data.redis.host=redis" \
      "spring.data.redis.port=6379" \
      "spring.data.redis.password=$REDIS_PASSWORD" \
      "springdoc.api-docs.path=/accounts-api-docs" \
      "springdoc.swagger-ui.path=/accounts-api-ui" \
      "management.endpoints.web.exposure.include=health,info,metrics,refresh,env" \
      "management.endpoint.health.show-details=ALWAYS" \
      "logging.pattern.console=$LOG_PATTERN" \
      "logging.level.root=INFO" \
      "logging.level.com.ebank.accounts=DEBUG" \
      "logging.level.io.r2dbc.postgresql=INFO"
    ;;
  recf)
    echo "[accounts-service] recf"
    vault kv put secret/ebank/accounts-service/recf \
      "server.port=8082" \
      "spring.r2dbc.url=${SPRING_R2DBC_URL_RECF:?required}" \
      "spring.r2dbc.username=${SPRING_R2DBC_USERNAME_RECF:?required}" \
      "spring.r2dbc.password=${ACCOUNTS_DB_PASSWORD_RECF:?required}" \
      "spring.flyway.url=${SPRING_FLYWAY_URL_RECF:?required}" \
      "spring.flyway.user=${SPRING_FLYWAY_USER_RECF:?required}" \
      "spring.flyway.password=${ACCOUNTS_DB_PASSWORD_RECF:?required}" \
      "spring.flyway.enabled=true" \
      "spring.flyway.locations=classpath:db/migration" \
      "spring.data.redis.host=${REDIS_HOST_RECF:?required}" \
      "spring.data.redis.port=6379" \
      "spring.data.redis.password=${REDIS_PASSWORD_RECF:?required}" \
      "management.endpoints.web.exposure.include=health,info,metrics,refresh" \
      "management.endpoint.health.show-details=WHEN_AUTHORIZED" \
      "logging.pattern.console=$LOG_PATTERN_RECF" \
      "logging.level.root=INFO" \
      "logging.level.com.ebank.accounts=INFO" \
      "logging.level.io.r2dbc.postgresql=WARN"
    ;;
  prod)
    echo "[accounts-service] prod"
    vault kv put secret/ebank/accounts-service/prod \
      "server.port=8082" \
      "spring.r2dbc.url=${SPRING_R2DBC_URL_PROD:?required}" \
      "spring.r2dbc.username=${SPRING_R2DBC_USERNAME_PROD:?required}" \
      "spring.r2dbc.password=${ACCOUNTS_DB_PASSWORD_PROD:?required}" \
      "spring.flyway.url=${SPRING_FLYWAY_URL_PROD:?required}" \
      "spring.flyway.user=${SPRING_FLYWAY_USER_PROD:?required}" \
      "spring.flyway.password=${ACCOUNTS_DB_PASSWORD_PROD:?required}" \
      "spring.flyway.enabled=true" \
      "spring.flyway.locations=classpath:db/migration" \
      "spring.data.redis.host=${REDIS_HOST_PROD:?required}" \
      "spring.data.redis.port=6379" \
      "spring.data.redis.password=${REDIS_PASSWORD_PROD:?required}" \
      "management.endpoints.web.exposure.include=health,info" \
      "management.endpoint.health.show-details=NEVER" \
      "logging.pattern.console=$LOG_PATTERN" \
      "logging.level.root=WARN" \
      "logging.level.com.ebank.accounts=WARN" \
      "logging.level.io.r2dbc.postgresql=WARN"
    ;;
esac

# =============================================================================
# TRANSACTION SERVICE — secret/ebank/transaction-service/{env}
# =============================================================================
case "$ENV" in
  docker)
    echo "[transaction-service] docker"
    vault kv put secret/ebank/transaction-service/docker \
      "server.port=8083" \
      "spring.data.mongodb.uri=$MONGO_URI" \
      "spring.kafka.bootstrap-servers=$KAFKA_BROKERS" \
      "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer" \
      "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer" \
      "spring.kafka.producer.properties.spring.json.add.type.headers=false" \
      "spring.data.redis.host=redis" \
      "spring.data.redis.port=6379" \
      "spring.data.redis.password=$REDIS_PASSWORD" \
      "springdoc.api-docs.path=/transactions-api-docs" \
      "springdoc.swagger-ui.path=/transactions-api-ui" \
      "management.endpoints.web.exposure.include=health,info,metrics,refresh,env" \
      "management.endpoint.health.show-details=ALWAYS" \
      "logging.pattern.console=$LOG_PATTERN" \
      "logging.level.root=INFO" \
      "logging.level.com.ebank.transactions=DEBUG" \
      "logging.level.org.springframework.data.mongodb=INFO"
    ;;
  recf)
    echo "[transaction-service] recf"
    vault kv put secret/ebank/transaction-service/recf \
      "server.port=8083" \
      "spring.data.mongodb.uri=${MONGO_URI_RECF:?required}" \
      "spring.kafka.bootstrap-servers=${KAFKA_BROKERS_RECF:?required}" \
      "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer" \
      "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer" \
      "spring.kafka.producer.properties.spring.json.add.type.headers=false" \
      "spring.data.redis.host=${REDIS_HOST_RECF:?required}" \
      "spring.data.redis.port=6379" \
      "spring.data.redis.password=${REDIS_PASSWORD_RECF:?required}" \
      "management.endpoints.web.exposure.include=health,info,metrics,refresh" \
      "management.endpoint.health.show-details=WHEN_AUTHORIZED" \
      "logging.pattern.console=$LOG_PATTERN_RECF" \
      "logging.level.root=INFO" \
      "logging.level.com.ebank.transactions=INFO" \
      "logging.level.org.springframework.data.mongodb=INFO"
    ;;
  prod)
    echo "[transaction-service] prod"
    vault kv put secret/ebank/transaction-service/prod \
      "server.port=8083" \
      "spring.data.mongodb.uri=${MONGO_URI_PROD:?required}" \
      "spring.kafka.bootstrap-servers=${KAFKA_BROKERS_PROD:?required}" \
      "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer" \
      "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer" \
      "spring.kafka.producer.properties.spring.json.add.type.headers=false" \
      "spring.data.redis.host=${REDIS_HOST_PROD:?required}" \
      "spring.data.redis.port=6379" \
      "spring.data.redis.password=${REDIS_PASSWORD_PROD:?required}" \
      "management.endpoints.web.exposure.include=health,info" \
      "management.endpoint.health.show-details=NEVER" \
      "logging.pattern.console=$LOG_PATTERN" \
      "logging.level.root=WARN" \
      "logging.level.com.ebank.transactions=WARN" \
      "logging.level.org.springframework.data.mongodb=WARN"
    ;;
esac

# =============================================================================
# GATEWAY — secret/ebank/gateway/{env}
# =============================================================================
case "$ENV" in
  docker)
    echo "[gateway] docker"
    vault kv put secret/ebank/gateway/docker \
      "server.port=8080" \
      "jwt.secret=$JWT_SECRET" \
      "auth.service.url=http://auth-service:8081" \
      "accounts.service.url=http://accounts-service:8082" \
      "transaction.service.url=http://transaction-service:8083" \
      "chatbot.service.url=http://chatbot-service:3001" \
      "notification.service.url=http://notification-service:3002" \
      "spring.data.redis.host=redis" \
      "spring.data.redis.port=6379" \
      "spring.data.redis.password=$REDIS_PASSWORD" \
      "springdoc.api-docs.path=/gateway-api-docs" \
      "springdoc.swagger-ui.path=/gateway-api-ui" \
      "management.endpoints.web.exposure.include=health,info,metrics,refresh,env" \
      "management.endpoint.health.show-details=ALWAYS" \
      "logging.pattern.console=$LOG_PATTERN" \
      "logging.level.root=INFO" \
      "logging.level.com.ebank.gateway=DEBUG" \
      "logging.level.org.springframework.cloud.gateway=INFO"
    ;;
  recf)
    echo "[gateway] recf"
    vault kv put secret/ebank/gateway/recf \
      "server.port=8080" \
      "jwt.secret=${JWT_SECRET_RECF:?required}" \
      "auth.service.url=${AUTH_SERVICE_URL_RECF:-http://auth-service:8081}" \
      "accounts.service.url=${ACCOUNTS_SERVICE_URL_RECF:-http://accounts-service:8082}" \
      "transaction.service.url=${TRANSACTION_SERVICE_URL_RECF:-http://transaction-service:8083}" \
      "chatbot.service.url=${CHATBOT_SERVICE_URL_RECF:-http://chatbot-service:3001}" \
      "notification.service.url=${NOTIFICATION_SERVICE_URL_RECF:-http://notification-service:3002}" \
      "spring.data.redis.host=${REDIS_HOST_RECF:?required}" \
      "spring.data.redis.port=6379" \
      "spring.data.redis.password=${REDIS_PASSWORD_RECF:?required}" \
      "management.endpoints.web.exposure.include=health,info,metrics,refresh" \
      "management.endpoint.health.show-details=WHEN_AUTHORIZED" \
      "logging.pattern.console=$LOG_PATTERN_RECF" \
      "logging.level.root=INFO" \
      "logging.level.com.ebank.gateway=INFO" \
      "logging.level.org.springframework.cloud.gateway=WARN"
    ;;
  prod)
    echo "[gateway] prod"
    vault kv put secret/ebank/gateway/prod \
      "server.port=8080" \
      "jwt.secret=${JWT_SECRET_PROD:?required}" \
      "auth.service.url=http://auth-service:8081" \
      "accounts.service.url=http://accounts-service:8082" \
      "transaction.service.url=http://transaction-service:8083" \
      "chatbot.service.url=http://chatbot-service:3001" \
      "notification.service.url=http://notification-service:3002" \
      "spring.data.redis.host=${REDIS_HOST_PROD:?required}" \
      "spring.data.redis.port=6379" \
      "spring.data.redis.password=${REDIS_PASSWORD_PROD:?required}" \
      "management.endpoints.web.exposure.include=health,info" \
      "management.endpoint.health.show-details=NEVER" \
      "logging.pattern.console=$LOG_PATTERN" \
      "logging.level.root=WARN" \
      "logging.level.com.ebank.gateway=WARN" \
      "logging.level.org.springframework.cloud.gateway=WARN"
    ;;
esac

# =============================================================================
# NOTIFICATION SERVICE — secret/ebank/notification-service/{env}
# =============================================================================
case "$ENV" in
  docker)
    echo "[notification-service] docker"
    vault kv put secret/ebank/notification-service/docker \
      "port=3002" \
      "kafka.brokers=$KAFKA_BROKERS" \
      "kafka.groupId=notification-group" \
      "kafka.clientId=notification-service" \
      "smtp.host=$SMTP_HOST" \
      "smtp.port=$SMTP_PORT" \
      "smtp.from=$SMTP_FROM" \
      "smtp.user=$SMTP_USER" \
      "smtp.password=$SMTP_PASSWORD" \
      "twilio.account-sid=$TWILIO_ACCOUNT_SID" \
      "twilio.auth-token=$TWILIO_AUTH_TOKEN" \
      "twilio.from-number=$TWILIO_FROM_NUMBER" \
      "logging.level=debug"
    ;;
  recf)
    echo "[notification-service] recf"
    vault kv put secret/ebank/notification-service/recf \
      "port=3002" \
      "kafka.brokers=${KAFKA_BROKERS_RECF:?required}" \
      "kafka.groupId=notification-group" \
      "kafka.clientId=notification-service" \
      "smtp.host=${SMTP_HOST_RECF:?required}" \
      "smtp.port=${SMTP_PORT_RECF:-587}" \
      "smtp.from=${SMTP_FROM_RECF:?required}" \
      "smtp.user=${SMTP_USER_RECF:?required}" \
      "smtp.password=${SMTP_PASSWORD_RECF:?required}" \
      "twilio.account-sid=${TWILIO_SID_RECF:?required}" \
      "twilio.auth-token=${TWILIO_TOKEN_RECF:?required}" \
      "twilio.from-number=${TWILIO_FROM_RECF:?required}" \
      "logging.level=info"
    ;;
  prod)
    echo "[notification-service] prod"
    vault kv put secret/ebank/notification-service/prod \
      "port=3002" \
      "kafka.brokers=${KAFKA_BROKERS_PROD:?required}" \
      "kafka.groupId=notification-group" \
      "kafka.clientId=notification-service" \
      "smtp.host=${SMTP_HOST_PROD:?required}" \
      "smtp.port=${SMTP_PORT_PROD:?required}" \
      "smtp.from=${SMTP_FROM_PROD:?required}" \
      "smtp.user=${SMTP_USER_PROD:?required}" \
      "smtp.password=${SMTP_PASSWORD_PROD:?required}" \
      "twilio.account-sid=${TWILIO_SID_PROD:?required}" \
      "twilio.auth-token=${TWILIO_TOKEN_PROD:?required}" \
      "twilio.from-number=${TWILIO_FROM_PROD:?required}" \
      "logging.level=warn"
    ;;
esac

echo ""
echo "==> Done. Config écrite pour ENV=$ENV"
echo ""
echo "Structure Vault:"
echo "  vault kv list secret/ebank"
echo "  vault kv list secret/ebank/auth-service"
echo "  vault kv get  -format=json secret/ebank/auth-service/$ENV"
