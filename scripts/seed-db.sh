#!/usr/bin/env bash
# Carga data.sql en la base de Postgres de docker-compose.
#
# Por qué existe: en producción (perfil "prod") data.sql nunca corre solo — son datos
# de prueba, y Hibernate recién crea las tablas cuando el backend arranca, así que ni
# siquiera el mecanismo automático de Postgres (docker-entrypoint-initdb.d) sirve acá:
# se ejecutaría antes de que las tablas existan. Este script espera a que el backend
# ya haya creado el esquema y recién ahí inserta los datos.
#
# Uso típico después de resetear el volumen:
#   docker compose down -v
#   docker compose up -d --build
#   ./scripts/seed-db.sh
set -euo pipefail
cd "$(dirname "$0")/.."

set -a
[ -f .env ] && source .env
set +a

DATA_SQL="Back/La Serranita entradas/src/main/resources/data.sql"
POSTGRES_USER="${POSTGRES_USER:-serranita}"
POSTGRES_DB="${POSTGRES_DB:-serranita}"

echo "Esperando a que el backend termine de crear el esquema..."
until curl -sf http://localhost:8080/api/ping > /dev/null 2>&1; do
  sleep 2
done

echo "Cargando data.sql en la base..."
docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" < "$DATA_SQL"

echo "Listo. Usuarios de prueba: admin/admin123, boletero.marta y boletero.juan con boletero123."
