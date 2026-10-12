#!/usr/bin/env bash
# Prepne zdroj čítania session bez reštartu a bez deployu (feature flag zo slide 12): PORTLET | REDIS.
set -euo pipefail
cd "$(dirname "$0")"
case "${1:-}" in
    PORTLET|REDIS) docker compose exec -T redis redis-cli SET crm:flags:session-read-source "$1" ;;
    *) echo "Použitie: $0 PORTLET|REDIS" >&2; exit 2 ;;
esac
