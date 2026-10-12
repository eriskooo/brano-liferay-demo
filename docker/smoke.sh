#!/usr/bin/env bash
# End-to-end overenie bežiaceho stacku cez nginx (http://localhost:8000): SSO login, blacklist, dashboard, AJAX,
# zdieľaná session v Redise, prepnutie flagu, výpadok Redisu, chybová stránka portálu, ACL čitateľa.
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
abs()   { case "$1" in http*) echo "$1" ;; *) echo "$B$1" ;; esac; }
fetch() { curl -s -L -b "$JAR" -c "$JAR" -o "$2" "$(abs "$1")"; }
post()  { local url out; url="$(abs "$1")"; out="$2"; shift 2; curl -s -L -b "$JAR" -c "$JAR" -o "$out" "$@" "$url"; }
# Atribút elementu s daným id (Liferay môže vrátiť HTML na jednom riadku).
attr_of() {
    grep -oE "<[a-z]+ id=\"$1\"[^>]*>" "$2" | head -1 | grep -oE " $3=\"[^\"]*\"" | head -1 \
        | sed -E "s/^ $3=\"//; s/\"$//; s/&amp;/\&/g"
}
rc()    { docker compose exec -T redis redis-cli "$@" | tr -d '\r'; }
login() {
    rm -f "$JAR"
    curl -s -c "$JAR" -b "$JAR" -o /dev/null -X POST "$B/sso/login" \
        -d "operator=$1" -d "customer=C-1001" -d "returnUrl=/web/crm/blacklist"
}

echo "Čakám na Liferay (prvý štart 1-3 min)..."
code=000
for _ in $(seq 1 90); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "$B/web/crm/blacklist")"
    [ "$code" = "200" ] && break
    sleep 5
done
[ "$code" = "200" ] || { echo "Liferay neodpovedá (posledný HTTP $code)"; exit 1; }

echo "== 1. Bez prihlásenia"
rm -f "$JAR"
fetch /web/crm/blacklist "$WORK/anon.html"
expect "blacklist bez SSO ukáže výzvu na prihlásenie" 'id="loginLink"' "$WORK/anon.html"
expect "hlavička je vložená z theme (runtime portlet)" 'id="sessionDebug"' "$WORK/anon.html"

echo "== 2. Prihlásenie v SSO mocku (správca, zákazník C-1001)"
login spravca
SSO_ID="$(grep SSO_ID "$JAR" | awk '{print $7}')"
if [ -n "$SSO_ID" ]; then ok "cookie SSO_ID=$SSO_ID"; else fail "cookie SSO_ID chýba"; fi
fetch /web/crm/blacklist "$WORK/search.html"
expect "hlavička ukazuje operátora" 'id="headerOperator"' "$WORK/search.html"
expect "operátor Správca" 'Správca' "$WORK/search.html"
expect "formulár vyhľadávania" 'id="searchForm"' "$WORK/search.html"

echo "== 3. Diakritika v action fáze"
post "$(attr_of searchForm "$WORK/search.html" action)" "$WORK/result1.html" \
    -d "firstName=M%C3%A1ria" -d lastName= -d identifierValue= -d state=
# Bajty UTF-8 explicitne: curl na Windows by z argumentu „Mária“ poslal %E1 (ANSI kódová stránka), prehliadač pošle UTF-8.
expect "Mária nájdená" 'id="detail-2"' "$WORK/result1.html"

echo "== 4. Vyhľadávanie a detail"
fetch /web/crm/blacklist "$WORK/search2.html"
post "$(attr_of searchForm "$WORK/search2.html" action)" "$WORK/result2.html" \
    -d firstName= -d lastName=Vzork -d identifierValue= -d state=
expect "výsledok: záznam 1" 'id="detail-1"' "$WORK/result2.html"
expect "výsledok: záznam 8" 'id="detail-8"' "$WORK/result2.html"
DETAIL="$(attr_of detail-1 "$WORK/result2.html" href)"
fetch "$DETAIL" "$WORK/detail.html"
expect "detail záznamu 1" 'AB123456' "$WORK/detail.html"

echo "== 5. Zmena stavu (BLACKLIST_MODIFY)"
post "$(attr_of changeForm "$WORK/detail.html" action)" "$WORK/changed.html" -d detailId=1 -d state=INACTIVE -d validTo=
expect "hláška v hlavičke" 'Stav záznamu bol zmenený.' "$WORK/changed.html"
expect "stav Neaktívny" 'id="recordState">Neaktívny' "$WORK/changed.html"

echo "== 6. Zdieľaná session v Redise"
rc HGETALL "crm:session:$SSO_ID" > "$WORK/redis.txt"
expect "Redis: posledný otvorený záznam" 'Peter Vzorka' "$WORK/redis.txt"
expect "Redis: posledné kritériá" 'Vzork' "$WORK/redis.txt"
TTL="$(rc TTL "crm:session:$SSO_ID")"
if [ "${TTL:-0}" -gt 0 ] 2>/dev/null; then ok "TTL $TTL s"; else fail "TTL $TTL"; fi

echo "== 7. Dashboard: zrušenie služby a JSON resource"
fetch /web/crm/dashboard "$WORK/products.html"
expect "produkty C-1001" 'id="deactivate-T-1"' "$WORK/products.html"
fetch "$(attr_of deactivate-T-1 "$WORK/products.html" href)" "$WORK/form.html"
expect "formulár pre Tarif Neobmedzený" 'Tarif Neobmedzený' "$WORK/form.html"
EARLIEST="$(abs "$(attr_of deactivationForm "$WORK/form.html" data-earliest-url)")&type=CUSTOMER_DEACTIVATION&noticeDate=2026-10-08"
curl -s -b "$JAR" -o "$WORK/earliest.json" "$EARLIEST"
expect "JSON najskorší dátum" '"earliestDate":"2026-11-07"' "$WORK/earliest.json"
code="$(curl -s -o /dev/null -w '%{http_code}' "$EARLIEST")"
if [ "$code" = "401" ]; then ok "AJAX bez SSO cookie -> 401"; else fail "AJAX bez SSO cookie -> $code (čakal 401)"; fi
post "$(attr_of deactivationForm "$WORK/form.html" action)" "$WORK/done.html" \
    -d productId=T-1 -d type=CUSTOMER_POSTPRE -d "noticeDate=$(date -u +%F)" -d effectiveDate=2099-12-31
expect "potvrdenie žiadosti" 'id="deactivationDone"' "$WORK/done.html"
expect "hláška o prijatí" 'Žiadosť o zrušenie bola prijatá.' "$WORK/done.html"

echo "== 8. Feature flag REDIS: fasáda číta zo zdieľanej session"
rc SET crm:flags:session-read-source REDIS > /dev/null
fetch "$DETAIL" "$WORK/detail-redis.html"
expect "hlavička: zdroj čítania REDIS" 'zdroj čítania = REDIS' "$WORK/detail-redis.html"
expect "posledný záznam prečítaný z Redisu" 'id="headerLastViewed"' "$WORK/detail-redis.html"
rc SET crm:flags:session-read-source PORTLET > /dev/null

echo "== 9. Výpadok Redisu nezhodí stránku"
docker compose stop redis > /dev/null 2>&1
fetch /web/crm/blacklist "$WORK/no-redis.html"
expect "stránka beží bez Redisu" 'id="searchForm"' "$WORK/no-redis.html"
docker compose start redis > /dev/null 2>&1

echo "== 10. Prepísaná chybová stránka portálu (hook)"
fetch /web/crm/neexistuje "$WORK/404.html"
expect "hooked status.jsp" 'id="crmPortalError"' "$WORK/404.html"

echo "== 11. Čitateľ: bez zmien a bez dashboardu"
login citatel
fetch /web/crm/blacklist "$WORK/reader.html"
expect "čitateľ vidí vyhľadávanie" 'id="searchForm"' "$WORK/reader.html"
expect_not "čitateľ nevidí „Nový záznam“" 'id="gotoCreateForm"' "$WORK/reader.html"
fetch /web/crm/dashboard "$WORK/reader-dashboard.html"
expect "čitateľ na dashboarde dostane 403" 'crm-error-403' "$WORK/reader-dashboard.html"

rm -rf "$WORK"
echo
if [ "$FAILS" -eq 0 ]; then echo "SMOKE OK"; else echo "SMOKE: $FAILS zlyhaní"; fi
exit "$FAILS"
