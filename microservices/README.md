# Documentation

## Config Setup / API / Testing

```sh

## How to setup Environments (install & config):

how to install java & maven on debian:
https://greenwebpage.com/community/how-to-install-java-on-debian-12/
https://phoenixnap.com/kb/install-maven-debian

launch spring app on vscode:
https://code.visualstudio.com/docs/java/java-spring-boot

## Accounts API

POST - Create Account

curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC123456789",
    "accountHolderName": "Jean Dupont",
    "email": "jean.dupont@email.com",
    "phoneNumber": "0612345678",
    "accountType": "SAVINGS",
    "balance": 1000.50,
    "address": "123 Rue de Paris, 75001 Paris",
    "status": "ACTIVE"
  }' | jq .

GET - Fetch All Accounts

curl -X GET http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" | jq .

GET - Fetch Account by ID (Replace 1 with actual ID)

curl -X GET http://localhost:8080/api/accounts/1 \
  -H "Content-Type: application/json" | jq .

PUT - Update Account (Replace 1 with actual ID)

curl -X PUT http://localhost:8080/api/accounts/1 \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "ACC123456789",
    "accountHolderName": "Jean Dupont UPDATED",
    "email": "jean.dupont@email.com",
    "phoneNumber": "0612345678",
    "accountType": "CHECKING",
    "balance": 2500.75,
    "address": "456 Avenue Lyon, 75002 Paris",
    "status": "ACTIVE"
  }' | jq .

DELETE - Delete Account (Replace 1 with actual ID)
curl -X DELETE http://localhost:8080/api/accounts/1 \
  -H "Content-Type: application/json"

### Transactions API:

GET toutes les transactions

curl -X GET http://localhost:8083/api/transactions

GET une transaction par ID

curl -X GET http://localhost:8083/api/transactions/{id}

GET transactions d'un compte

curl -X GET http://localhost:8083/api/transactions/account/{accountId}

POST créer une transaction (TRANSFER)

curl -X POST http://localhost:8083/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": "acc-001",
    "toAccountId": "acc-002",
    "amount": 150.00,
    "type": "TRANSFER",
    "description": "Virement mensuel"
  }'

POST créer un dépôt

curl -X POST http://localhost:8083/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "toAccountId": "acc-001",
    "amount": 500.00,
    "type": "DEPOSIT",
    "description": "Dépôt initial"
  }'

POST créer un retrait

curl -X POST http://localhost:8083/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": "acc-001",
    "amount": 50.00,
    "type": "WITHDRAWAL"
  }'

swagger API:

access  all service documentation:
http://localhost:8081/swagger-ui/index.html

Java Melody: (monitoring)

JavaMelody is a lightweight performance monitoring tool for Java applications. It helps track memory usage, SQL queries, HTTP requests, and more in real-time via a simple web UI.
http://localhost:8081/monitoring
check these metrics:
Memory Usage, Database Query Performance, Slow HTTP Requests, Garbage Collection Performance.

Spring ADMIN

access to spring admin
http://localhost:8081/admin

#### Testing

Unit Test (ONLY):

mvn clean test

Unit + integration Tests + coverage:

mvn clean test

Check coverage:

xdg-open target/site/jacoco/index.html
file:///home/sam/Desktop/ebank/accounts/target/site/jacoco/index.html

### Gateway

// 200
curl http://localhost:8080/actuator/health

### AUTH service

Test 1 Register:

curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@test.comh","password":"Secret123!"}' | jq

curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","email":"bob@test.com","password":"Secret123!"}' | jq

Test Login:

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.com","password":"Secret123!"}' | jq -r '.accessToken')
echo $TOKEN

curl -s http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" | jq

Logout:

curl -s -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer $TOKEN"

Test error 401:

curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN"

Validation error format:

curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"bad"}' | jq

````

## Docker / Docker compose

```sh
docker-compose up --build

Verify Config Server is serving config

curl http://localhost:8888/gateway/docker
curl http://localhost:8888/auth-service/docker
curl http://localhost:8888/accounts-service/docker
curl http://localhost:8888/transaction-service/docker

Verify Vault secrets were seeded

curl -H "X-Vault-Token: root" http://localhost:8200/v1/secret/data/ebank/auth-service

Verify each service picked up its config

curl http://localhost:8080/actuator/health   # gateway
curl http://localhost:8081/actuator/health   # auth
curl http://localhost:8082/actuator/health   # accounts
curl http://localhost:8083/actuator/health   # transactions
curl http://localhost:8888/actuator/env      # config server — shows all resolved properties

### Vault

access vault:
http://localhost:8200/ui/vault/secrets
root (token)

### Notification service

lauch services

docker compose up

test notification service up

curl http://localhost:3002/health

check logs notification service:

docker logs notification_service

Kafka UI:

Via Kafka UI (http://localhost:8090) → Topics → notification-events → Produce message :
{
  "type": "EMAIL",
  "recipient": "test@example.com",
  "subject": "Test notification",
  "body": "pCeci est un test/p",
  "occurredAt": "2026-04-04T12:00:00Z"
}

check emails:

Open http://localhost:8025 → email appear there.

### Docker compose version

docker compose up --build

Auth via gateway:

curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"joe","email":"joe@test.com","password":"Secret123!"}' | jq

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.com","password":"Secret123!"}' | jq -r '.accessToken')
echo $TOKEN

curl http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN"

curl http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN"

notifications:
http://localhost:8025

kafka ui:

Via Kafka UI (http://localhost:8090) → Topics → notification-events → Produce message :
{
  "type": "EMAIL",
  "recipient": "test@example.com",
  "subject": "Test notification",
  "body": "pCeci est un test/p",
  "occurredAt": "2026-04-04T12:00:00Z"
}

vault:
http://localhost:8200

curl http://localhost:8081/actuator/health  # auth
curl http://localhost:8082/actuator/health  # accounts
curl http://localhost:8083/actuator/health  # transactions
``

## Minikube version:

```sh
## Prerequisites

# Install tools
minikube start --driver=docker --cpus=4 --memory=8192 --disk-size=40g
minikube addons enable ingress
minikube addons enable metrics-server

# Install Helm
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

## build docker image inside minikube

# Point Docker CLI at minikube's daemon (CRITICAL — do this in every terminal)
eval $(minikube docker-env)

# Build all service images
docker build -t ebank-auth:local        ./auth
docker build -t ebank-accounts:local    ./accounts
docker build -t ebank-transactions:local ./transactions
docker build -t ebank-gateway:local     ./gateway
docker build -t ebank-notification:local ./notifications
docker build -t ebank-frontend:local    ./frontend

# Verify they're in minikube
docker images | grep ebank

# create namespace
kubectl create namespace ebank-local

# deploy vault
kubectl apply -f k8s/vault-dev.yaml -n ebank-local
kubectl wait --for=condition=ready pod -l app=vault -n ebank-local --timeout=60s

## deploy infra (postgres, mongo, redis, kafka)
kubectl apply -f k8s/infra-local.yaml -n ebank-local

# Wait for all infra to be ready
kubectl wait --for=condition=ready pod -l tier=infra -n ebank-local --timeout=180s

## deploy helm chart
helm upgrade --install ebank ./helm/ebank \
  -f helm/ebank/values.yaml \
  -f helm/ebank/values-local.yaml \
  --namespace ebank-local \
  --timeout 15m

## check pods, jobs, services, ... are RUNNING
kubectl get pods -n ebank-local
kubectl get jobs -n ebank-local
kubectl get all -n ebank-local

## Test API
# Open a port-forward to the gateway
kubectl port-forward svc/ebank-ebank-gateway -n ebank-local 8080:8080 &

# Register a user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@bank.com","password":"Test1234!","firstName":"Alice","lastName":"Smith"}'

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@bank.com","password":"Test1234!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

# Create a bank account (protected route)
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"accountNumber":"ACCT00000001","accountHolderName":"Alice Smith","email":"alice@bank.com","phoneNumber":"0601020304","accountType":"SAVINGS","balance":500.00,"status":"ACTIVE"}'

# Get bank accounts  
curl -X GET http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN"

## Access Frontend
# Get minikube IP
minikube ip   # e.g. 192.168.49.2

# Add to /etc/hosts
echo "$(minikube ip) ebank.local api.ebank.local" | sudo tee -a /etc/hosts

# Open browser at http://ebank.local
```

## Scale up/down:

```sh
## Scaler UP/DOWN

# Scale a deployment up
kubectl scale deployment ebank-ebank-auth -n ebank-local --replicas=3
# Scale down
kubectl scale deployment ebank-ebank-auth -n ebank-local --replicas=1

# Check
kubectl get pods -n ebank-local | grep auth

## Auto Scaler UP/DOWN
helm upgrade ebank ./helm/ebank -f helm/ebank/values.yaml -f helm/ebank/values-local.yaml -n ebank-local

# Trigger load to see HPA in action
kubectl run -it load-gen --image=busybox --rm -n ebank-local -- /bin/sh -c \
  "while true; do wget -qO- http://ebank-ebank-gateway:8080/actuator/health; done"

# Watch HPA
kubectl get hpa -n ebank-local -w

```

```sh
# CPU and memory per pod
kubectl top pods -n ebank-local

# Node resource pressure
kubectl top nodes

# HPA status
kubectl get hpa -n ebank-local
```