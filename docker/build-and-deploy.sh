#!/usr/bin/env bash
# Build legacy appky a SSO mocku, štart Docker stacku a hot deploy WAR-ov do Liferay.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

mvn -q -B -f "$ROOT/legacy/pom.xml" verify
mvn -q -B -f "$ROOT/sso-mock/pom.xml" verify
mvn -q -B -f "$ROOT/crm-web/pom.xml" verify
cp "$ROOT/legacy/sso-portal-api/target/sso-portal-api.jar" "$ROOT/docker/liferay/sso-portal-api.jar"
mkdir -p "$ROOT/docker/deploy"

cd "$ROOT/docker"
docker compose up -d --build

# Theme pred portletmi: portlety ho majú v required-deployment-contexts. Liferay WAR po spracovaní z deploy/ zmaže.
cp "$ROOT/legacy/customercare-theme/target/customercare-theme.war" deploy/
cp "$ROOT/legacy/customercare-portlets/target/customercare-portlet.war" deploy/

# RedisInsight 3.x nepreberá pripojenie z premenných prostredia – databázu zdieľanej session zaregistrujeme cez jeho API.
for _ in $(seq 1 30); do curl -s -o /dev/null http://localhost:5540/api/databases && break; sleep 2; done
curl -s http://localhost:5540/api/databases | grep -q crm-shared-session     || curl -s -o /dev/null -X POST http://localhost:5540/api/databases -H 'Content-Type: application/json'         -d '{"name":"crm-shared-session","host":"redis","port":6379}'
echo "Hotovo. Liferay štartuje 1-3 min: docker compose -f docker/docker-compose.yml logs -f liferay"
