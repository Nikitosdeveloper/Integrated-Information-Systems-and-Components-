#!/bin/bash
# Создаёт по базе на каждый сервис и применяет схему (01_schema.sql) и тестовые данные (02_seed.sql).
set -e
for db in identity_db borrower_db application_db statement_db analysis_db scoring_db; do
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE $db"
  for f in /sql/$db/*.sql; do
    psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$db" -f "$f"
  done
done
