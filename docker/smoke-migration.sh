#!/usr/bin/env bash
# Súbeh starej (Liferay) a novej (Spring Boot) appky cez nginx (http://localhost:8000): strangler, SSO identita
# v oboch appkách, zdieľaná session v Redise a vplyv flagu starej appky (PORTLET vs REDIS).
set -u
cd "$(dirname "$0")"
B="http://localhost:8000"
L="http://localhost:8080"   # Liferay priamo (starý blacklist mimo nginx – na :8000 ho presmeruje strangler)
WORK="$(mktemp -d)"
JAR="$WORK/cookies.txt"
FAILS=0

ok()   { echo "OK    $1"; }
fail() { echo "FAIL  $1"; FAILS=$((FAILS + 1)); }
expect()     { if grep -qF -- "$2" "$3"; then ok "$1"; else fail "$1 (chýba: $2)"; fi; }
expect_not() { if grep -qF -- "$2" "$3"; then fail "$1 (nemá tam byť: $2)"; else ok "$1"; fi; }
fetch() { curl -s -L -b "$JAR" -c "$JAR" -o "$2" "$B$1"; }
post()  { local path="$1" out="$2"; shift 2; curl -s -L -b "$JAR" -c "$JAR" -o "$out" "$@" "$B$path"; }
rc()    { docker compose exec -T redis redis-cli "$@" | tr -d '\r'; }
# Atribút elementu s daným id (Liferay môže vrátiť HTML na jednom riadku).
attr_of() {
    grep -oE "<[a-z]+ id=\"$1\"[^>]*>" "$2" | head -1 | grep -oE " $3=\"[^\"]*\"" | head -1 \
        | sed -E "s/^ $3=\"//; s/\"$//; s/&amp;/\&/g"
}
# Obe appky zdieľajú DB blacklistu – výsledok nesmie závisieť od toho, čo zmenil predchádzajúci beh.
reset_db() { docker compose exec -T postgres psql -q -U postgres -d crm < postgres/init/02-blacklist-seed.sql > /dev/null; }
cookie() { grep -E "[[:space:]]$1[[:space:]]" "$JAR" | awk '{print $7}' | head -1; }
login() {
    rm -f "$JAR"
    curl -s -c "$JAR" -b "$JAR" -o /dev/null -X POST "$B/sso/login" \
        -d "operator=$1" -d "customer=C-1001" -d "returnUrl=/crm/blacklist/search"
}

echo "Čakám na Liferay a crm-web..."
for _ in $(seq 1 90); do
    a="$(curl -s -o /dev/null -w '%{http_code}' "$B/web/crm/dashboard")"
    b="$(curl -s -o /dev/null -w '%{http_code}' "$B/crm/blacklist/search")"
    [ "$a" = "200" ] && [ "$b" = "302" ] && curl -s "$B/web/crm/dashboard" | grep -q 'id="sessionDebug"' && break
    sleep 5
done

reset_db

echo "== 1. Strangler v nginx"
loc="$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' "$B/web/crm/blacklist")"
if [ "$loc" = "302 $B/crm/blacklist/search" ]; then ok "GET starej stránky -> nová appka"; else fail "GET starej stránky: $loc"; fi
loc="$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' -X POST \
    "$B/web/crm/blacklist?p_p_id=blacklist_WAR_customercareportlet&p_p_lifecycle=1")"
if [ "$loc" = "302 $B/crm/blacklist/search" ]; then ok "strangler POST (stará action URL) -> 302"; else fail "strangler POST: $loc"; fi

echo "== 2. Bez SSO nová appka presmeruje na SSO login"
loc="$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' "$B/crm/blacklist/search")"
case "$loc" in "302 $B/sso/login?returnUrl=%2Fcrm%2Fblacklist%2Fsearch") ok "redirect na SSO login" ;; *) fail "redirect na SSO login: $loc" ;; esac

echo "== 3. Prihlásenie (správca) – identita v oboch appkách, cookies sa nebijú"
login spravca
SSO_ID="$(cookie SSO_ID)"
fetch /web/crm/dashboard "$WORK/dash0.html"
JSESSION_BEFORE="$(cookie JSESSIONID)"
fetch /crm/blacklist/search "$WORK/search.html"
expect "nová appka: operátor zo SSO" 'id="headerOperator"' "$WORK/search.html"
expect "nová appka: Správca" 'Správca' "$WORK/search.html"
expect "nová appka: ladiaci riadok so SSO_ID" "SSO_ID = $SSO_ID" "$WORK/search.html"
expect "stará appka (dashboard): operátor zo SSO" 'id="headerOperator"' "$WORK/dash0.html"

echo "== 4. Blacklist v novej appke"
# Všetky polia formulára ako prehliadač – kritériá sú v @SessionAttributes a nevyplnené pole by ostalo z minulého hľadania.
post /crm/blacklist/searchAction "$WORK/result1.html" -d "firstName=M%C3%A1ria" -d lastName= -d identifierValue= -d state=
expect "diakritika: Mária nájdená" 'Mária Príkladová' "$WORK/result1.html"
post /crm/blacklist/searchAction "$WORK/result2.html" -d firstName= -d lastName=Vzork -d identifierValue= -d state=
expect "výsledok: Peter Vzorka" 'Peter Vzorka' "$WORK/result2.html"
post "/crm/blacklist/changeAction?detailId=7" "$WORK/changed.html" -d state=INACTIVE -d validTo=
expect "zmena stavu: hláška po PRG" 'Stav záznamu bol zmenený.' "$WORK/changed.html"
fetch "/crm/blacklist/detail?detailId=1" "$WORK/detail.html"
expect "detail: AB123456" 'AB123456' "$WORK/detail.html"
code="$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Requested-With: XMLHttpRequest' \
    "$B/crm/blacklist/resource/refreshCreateConditionTypes?stateReason=DEBT")"
if [ "$code" = "401" ]; then ok "AJAX bez SSO -> 401"; else fail "AJAX bez SSO -> $code"; fi
JSESSION_AFTER="$(cookie JSESSIONID)"
if [ -n "$JSESSION_BEFORE" ] && [ "$JSESSION_BEFORE" = "$JSESSION_AFTER" ]; then ok "JSESSIONID Liferay nezmenený"; else fail "JSESSIONID: '$JSESSION_BEFORE' -> '$JSESSION_AFTER'"; fi
if [ -n "$(cookie CRM_SESSION)" ]; then ok "nová appka má vlastnú cookie CRM_SESSION"; else fail "CRM_SESSION chýba"; fi

echo "== 5. Zdieľaná session v Redise"
rc HGETALL "crm:session:$SSO_ID" > "$WORK/redis.txt"
expect "Redis: posledný záznam z novej appky" '{"id":1,"fullName":"Peter Vzorka"}' "$WORK/redis.txt"
expect "Redis: posledné kritériá z novej appky" '"lastName":"Vzork"' "$WORK/redis.txt"

echo "== 6. Flag PORTLET: stará appka číta svoju PortletSession – zápis novej appky nevidí"
rc SET crm:flags:session-read-source PORTLET > /dev/null
fetch /web/crm/dashboard "$WORK/dash-portlet.html"
expect_not "dashboard bez posledného záznamu" 'id="headerLastViewed"' "$WORK/dash-portlet.html"

echo "== 7. Flag REDIS: stará appka číta zo zdieľanej session"
rc SET crm:flags:session-read-source REDIS > /dev/null
fetch /web/crm/dashboard "$WORK/dash-redis.html"
expect "dashboard (Liferay) ukáže záznam otvorený v novej appke" 'Naposledy otvorený záznam: Peter Vzorka' "$WORK/dash-redis.html"
fetch /crm/blacklist/search "$WORK/search-redis.html"
expect "nová appka zobrazí flag starej appky" 'flag starej appky = REDIS' "$WORK/search-redis.html"

echo "== 8. Zmena zákazníka v SSO platí v oboch appkách"
curl -s -b "$JAR" -c "$JAR" -o /dev/null -X POST "$B/sso/customer" -d customer=C-1002 -d returnUrl=/crm/blacklist/search
fetch /crm/blacklist/search "$WORK/search-c2.html"
expect "nová appka: zákazník C-1002" 'C-1002' "$WORK/search-c2.html"
fetch /web/crm/dashboard "$WORK/dash-c2.html"
expect "dashboard: produkty C-1002" 'Firemný tarif' "$WORK/dash-c2.html"

echo "== 9. Čitateľ: zmena v novej appke je zakázaná"
login citatel
code="$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -c "$JAR" -X POST \
    "$B/crm/blacklist/changeAction?detailId=7" -d state=ACTIVE -d validTo=)"
if [ "$code" = "403" ]; then ok "čitateľ -> 403"; else fail "čitateľ -> $code"; fi

echo "== 10. Výpadok Redisu nezhodí novú appku (stránka aj akcia)"
login spravca
docker compose stop redis > /dev/null 2>&1
fetch /crm/blacklist/search "$WORK/no-redis.html"
expect "nová appka beží bez Redisu" 'id="headerOperator"' "$WORK/no-redis.html"
started=$(date +%s)
post "/crm/blacklist/changeAction?detailId=5" "$WORK/no-redis-action.html" -d state=INACTIVE -d validTo=
took=$(( $(date +%s) - started ))
expect "akcia (POST + redirect) počas výpadku prejde" 'Tomáš Fiktívny' "$WORK/no-redis-action.html"
if [ "$took" -le 10 ]; then ok "akcia počas výpadku trvala ${took} s (<= 10 s)"; else fail "akcia počas výpadku trvala ${took} s"; fi
docker compose start redis > /dev/null 2>&1

echo "== 11. Zdieľaná DB: zmena z novej appky je vidieť v starej"
# Záznam 7 zmenila nová appka v kroku 4 (INACTIVE); Liferay ho číta z tej istej DB. Krok je posledný – detail v Liferay
# prepíše „posledný otvorený záznam“ v zdieľanej session, na ktorý sa spoliehajú kroky 6 a 7. Starý blacklist ide mimo nginx (strangler).
curl -s -L -b "$JAR" -c "$JAR" -o "$WORK/l-search.html" "$L/web/crm/blacklist"
curl -s -L -b "$JAR" -c "$JAR" -o "$WORK/l-result.html" -d firstName= -d "lastName=Modelov" -d identifierValue= -d state= \
    "$(attr_of searchForm "$WORK/l-search.html" action)"
curl -s -L -b "$JAR" -c "$JAR" -o "$WORK/l-detail.html" "$(attr_of detail-7 "$WORK/l-result.html" href)"
expect "Liferay: záznam 7 je Neaktívny" 'id="recordState">Neaktívny' "$WORK/l-detail.html"

rc SET crm:flags:session-read-source PORTLET > /dev/null
reset_db
rm -rf "$WORK"
echo
if [ "$FAILS" -eq 0 ]; then echo "MIGRATION SMOKE OK"; else echo "MIGRATION SMOKE: $FAILS zlyhaní"; fi
exit "$FAILS"
