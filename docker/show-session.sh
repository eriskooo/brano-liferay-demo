#!/usr/bin/env bash
# Zdieľaná session „fyzicky“: flag zdroja čítania a všetky hashe crm:session:{SSO_ID} s TTL a obsahom.
cd "$(dirname "$0")"
rc() { docker compose exec -T redis redis-cli "$@" | tr -d '\r'; }

flag="$(rc GET crm:flags:session-read-source)"
echo "flag crm:flags:session-read-source = ${flag:-(nenastavený = PORTLET)}"
keys="$(rc --scan --pattern 'crm:session:*')"
[ -z "$keys" ] && echo "(žiadna zdieľaná session – prihlás sa a otvor stránku CRM)"
for key in $keys; do
    echo
    echo "== $key  (TTL $(rc TTL "$key") s)"
    rc HGETALL "$key" | paste - -
done
