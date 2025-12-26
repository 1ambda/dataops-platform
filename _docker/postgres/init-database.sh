#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE application;
    CREATE USER application WITH ENCRYPTED PASSWORD 'application';
    GRANT ALL PRIVILEGES ON DATABASE application TO application;
    GRANT ALL ON ALL TABLES IN SCHEMA public TO application;

    CREATE DATABASE keycloak;
    CREATE USER keycloak WITH ENCRYPTED PASSWORD 'keycloak';
    GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
EOSQL