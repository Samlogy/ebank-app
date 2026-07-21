-- ============================================================
-- eBank PostgreSQL initialization script
-- Creates one database per microservice (Database-per-Service pattern)
--
-- WHY: Each microservice owns its data schema independently.
-- PROs : isolation forte, déploiement indépendant, schéma propre par domaine
-- CONs : pas de jointures cross-services, cohérence éventuelle à gérer
-- ============================================================

CREATE DATABASE auth_db;
CREATE DATABASE accounts_db;
CREATE DATABASE chatbot_db;

-- ============================================================
-- Chatbot RAG store — enable the pgvector extension inside chatbot_db.
-- Spring AI's PgVectorStore also runs "CREATE EXTENSION IF NOT EXISTS vector"
-- when initialize-schema=true, but enabling it here makes the DB self-describing
-- and works even if schema initialization is turned off.
-- Requires the pgvector/pgvector image (see docker-compose.yml).
-- ============================================================
\connect chatbot_db
CREATE EXTENSION IF NOT EXISTS vector;
