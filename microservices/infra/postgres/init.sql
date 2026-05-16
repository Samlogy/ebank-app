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
