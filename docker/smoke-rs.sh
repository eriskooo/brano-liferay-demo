#!/usr/bin/env bash
# Rust kópia (crm-web-rs) v bežiacom stacku cez nginx (http://localhost:8000): SSO identita, zdieľaná session
# s Java appkou (crm-web), AJAX, ACL, vlastná session cookie a návrat po výpadku Redisu.
# Spúšťať po docker/build-and-deploy.sh --with-rs a nie súčasne so smoke-migration.sh (oba vypínajú Redis).
set -u
cd "$(dirname "$0")"
B="http://localhost:8000"
WORK="$(mktemp -d)"
JAR="$WORK/cookies.txt"
FAILS=0

ok()   { echo "OK    $1"; }
fail() { echo "FAIL  $1"; FAILS=$((FAILS + 1)); }
expect()     { if grep -qF -- "$2" "$3"; then ok "$1"; else fail "$1 (chýba: $2)"; fi; }
expect_not() { if grep -qF -- "$2" "$3"; then fail "$1 (nemá tam byť: $2)"; else ok "$1"; fi; }
expect_code() { if [ "$2" = "$3" ]; then ok "$1"; else fail "$1 (HTTP $3, čakal som $2)"; fi; }
fetch() { curl -s -L -b "$JAR" -c "$JAR" -o "$2" "$B$1"; }
post()  { local path="$1" out="$2"; shift 2; curl -s -L -b "$JAR" -c "$JAR" -o "$out" "$@" "$B$path"; }
code()  { curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -c "$JAR" "$@"; }
rc()    { docker compose exec -T redis redis-cli "$@" | tr -d '\r'; }
cookie() { awk -v n="$1" '$6 == n {print $7}' "$JAR" | head -1; }
cookie_path() { awk -v n="$1" '$6 == n {print $3}' "$JAR" | head -1; }
login() {
    rm -f "$JAR"
    curl -s -c "$JAR" -b "$JAR" -o /dev/null -X POST "$B/sso/login" \
        -d "operator=$1" -d "customer=C-1001" -d "returnUrl=/crm-rs/blacklist/search"
}

echo "== 0. Rust appka beží za nginx"
if [ "$(curl -s -o /dev/null -w '%{http_code}' "$B/crm-rs/blacklist/search")" != "302" ]; then
    echo "FAIL  /crm-rs neodpovedá – spusti najprv bash docker/build-and-deploy.sh --with-rs"
    exit 1
fi

echo "== 1. Bez SSO presmeruje na SSO login s návratom do Rust appky"
loc="$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' "$B/crm-rs/blacklist/search")"
case "$loc" in "302 $B/sso/login?returnUrl=%2Fcrm-rs%2Fblacklist%2Fsearch") ok "redirect na SSO login" ;; *) fail "redirect: $loc" ;; esac

echo "== 2. Prihlásenie (správca) – identita zo SSO"
login spravca
SSO_ID="$(cookie SSO_ID)"
fetch /crm-rs/blacklist/search "$WORK/search.html"
expect "operátor zo SSO" 'id="headerOperator"' "$WORK/search.html"
expect "Správca" 'Správca' "$WORK/search.html"
expect "ladiaci riadok Rust appky" 'Redis (Rust appka)' "$WORK/search.html"
expect "SSO_ID v ladiacom riadku" "SSO_ID = $SSO_ID" "$WORK/search.html"
expect "CSS z Liferay theme cez nginx" '/html/themes/classic/css/main.css' "$WORK/search.html"
expect_code "statické CSS z crm-web" 200 "$(code "$B/crm-rs/css/crm.css")"

echo "== 3. Vyhľadávanie a detail"
post /crm-rs/blacklist/searchAction "$WORK/result1.html" -d "firstName=M%C3%A1ria" -d lastName= -d identifierValue= -d state=
expect "diakritika: Mária nájdená" 'Mária Príkladová' "$WORK/result1.html"
if [ "$(cookie_path CRM_RS_SESSION)" = "/crm-rs" ]; then ok "vlastná cookie CRM_RS_SESSION s Path=/crm-rs"; else fail "CRM_RS_SESSION: '$(cookie_path CRM_RS_SESSION)'"; fi
post /crm-rs/blacklist/searchAction "$WORK/result2.html" -d firstName= -d lastName=Vzork -d identifierValue= -d state=
expect "výsledok: Peter Vzorka" 'Peter Vzorka' "$WORK/result2.html"
fetch "/crm-rs/blacklist/detail?detailId=1" "$WORK/detail.html"
expect "detail: AB123456" 'AB123456' "$WORK/detail.html"

echo "== 4. Zdieľaná session v Redise (zapísala Rust appka)"
rc HGETALL "crm:session:$SSO_ID" > "$WORK/redis.txt"
expect "Redis: posledný záznam" '{"id":1,"fullName":"Peter Vzorka"}' "$WORK/redis.txt"
expect "Redis: posledné kritériá" '"lastName":"Vzork"' "$WORK/redis.txt"

echo "== 5. Java appka (crm-web) vidí, čo zapísala Rust appka"
fetch /crm/blacklist/search "$WORK/java.html"
expect "Java hlavička: posledný záznam z Rust appky" 'Naposledy otvorený záznam: Peter Vzorka' "$WORK/java.html"

echo "== 6. Hláška z Rust appky sa zobrazí raz – v Java appke"
expect_code "zmena stavu v Rust appke (PRG)" 302 "$(code -X POST "$B/crm-rs/blacklist/changeAction?detailId=7" -d state=INACTIVE -d validTo=)"
fetch /crm/blacklist/search "$WORK/java-msg.html"
expect "Java appka zobrazí hlášku z Rust appky" 'Stav záznamu bol zmenený.' "$WORK/java-msg.html"
fetch /crm-rs/blacklist/search "$WORK/rs-after.html"
expect_not "hláška sa nezobrazí druhýkrát" 'Stav záznamu bol zmenený.' "$WORK/rs-after.html"

echo "== 7. AJAX"
curl -s -b "$JAR" -H 'X-Requested-With: XMLHttpRequest' -o "$WORK/conditions.html" \
    "$B/crm-rs/blacklist/resource/refreshCreateConditionTypes?stateReason=DEBT"
expect "podmienky pre DEBT" 'REQUIRE_DEPOSIT' "$WORK/conditions.html"
expect_not "bez BLOCK_ALL" 'BLOCK_ALL' "$WORK/conditions.html"
expect_code "AJAX bez SSO -> 401" 401 "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Requested-With: XMLHttpRequest' \
    "$B/crm-rs/blacklist/resource/refreshCreateConditionTypes?stateReason=DEBT")"

echo "== 8. Čitateľ"
login citatel
expect_code "čitateľ: zmena -> 403" 403 "$(code -X POST "$B/crm-rs/blacklist/changeAction?detailId=7" -d state=ACTIVE -d validTo=)"
expect_code "čitateľ: nový záznam -> 403" 403 "$(code "$B/crm-rs/blacklist/create")"

echo "== 9. Výpadok a návrat Redisu"
login spravca
SSO_ID="$(cookie SSO_ID)"
docker compose stop redis > /dev/null 2>&1
fetch /crm-rs/blacklist/search "$WORK/no-redis.html"
expect "Rust appka beží bez Redisu" 'id="headerOperator"' "$WORK/no-redis.html"
started=$(date +%s)
post "/crm-rs/blacklist/changeAction?detailId=5" "$WORK/no-redis-action.html" -d state=INACTIVE -d validTo=
took=$(( $(date +%s) - started ))
expect "akcia počas výpadku prejde" 'Tomáš Fiktívny' "$WORK/no-redis-action.html"
if [ "$took" -le 10 ]; then ok "akcia počas výpadku trvala ${took} s"; else fail "akcia počas výpadku trvala ${took} s"; fi
docker compose start redis > /dev/null 2>&1
# Spadnuté spojenie sa obnoví v tej istej operácii – zapisuje už prvý request po reštarte.
fetch "/crm-rs/blacklist/detail?detailId=4" "$WORK/after.html"
rc HGET "crm:session:$SSO_ID" APP_LAST_VIEWED_RECORD > "$WORK/after-redis.txt"
expect "prvý request po návrate Redisu zapisuje" 'Eva Ukážková' "$WORK/after-redis.txt"

rm -rf "$WORK"
echo
if [ "$FAILS" -eq 0 ]; then echo "RS SMOKE OK"; else echo "RS SMOKE: $FAILS zlyhaní"; fi
exit "$FAILS"
