#!/usr/bin/env bash
set -euo pipefail

set -a
source .env.dev
source .env.application
set +a

: "${DB_RESET:=0}"

if [[ "$DB_RESET" == "1" ]]; then
  echo "[db] reset + seed..."
  docker exec -i multiapp-postgres psql -U "$APP_DB_USER" -d "$APP_DB" <<'SQL'
-- 清空业务schema; 删除app schema下所有表/数据, 然后重建空的schema
drop schema if exists app cascade;
create schema app;
SQL
fi

./mvnw spring-boot:run -Dspring-boot.run.profiles=dev