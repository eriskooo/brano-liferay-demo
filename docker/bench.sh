#!/usr/bin/env bash
# Záťažový test Java crm-web vs Rust crm-web-rs (výsledky a postup v docs/BENCHMARK.md).
# Záťaž ide z kontajnera (oha) priamo na appky v sieti compose – bez nginx a Liferay, ktoré by meranie skreslili.
# Použitie: bash docker/bench.sh [súbežnosť=50] [trvanie=20s]   (scenáre: SCENARIOS="css search ajax post churn")
# Potrebuje bežiace: docker compose up -d redis sso-mock crm-web crm-web-rs
set -u
cd "$(dirname "$0")"
CONC="${1:-50}"
DUR="${2:-20s}"
WARMUP="${WARMUP:-10s}"
SCENARIOS="${SCENARIOS:-css search ajax post churn}"
NET="crm-legacy_default"
JAVA="http://crm-web:8080/crm"
RUST="http://crm-web-rs:8080/crm-rs"
OHA_IMAGE="ghcr.io/hatoo/oha:1.16.0"
CURL_IMAGE="curlimages/curl:8.10.1"
FORM_TYPE="application/x-www-form-urlencoded"
SEARCH_FORM="firstName=&lastName=Vzork&identifierValue=&state="
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

curl_in_net() { docker run --rm --network "$NET" "$CURL_IMAGE" -s "$@"; }
oha() { docker run --rm --network "$NET" "$OHA_IMAGE" --no-tui --output-format json -c "$CONC" --redirect 0 "$@"; }
# Hodnota cookie z hlavičiek Set-Cookie (curl -D -).
cookie_from() { tr -d '\r' | grep -io "set-cookie: $1=[^;]*" | head -1 | cut -d= -f2; }
# Číslo z JSON výstupu oha (sekcia metrics, latencie v ms).
metric() { tr -d '\n ' < "$1" | grep -o '"metrics":{.*' | grep -o "\"$2\":[0-9.]*" | head -1 | cut -d: -f2; }
stats() { docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' \
    | grep -E 'crm-web|sso-mock' | sed 's/crm-legacy-//; s/-1 / /' | awk '{printf "%s %s %s; ", $1, $2, $3}'; }

report() { # app scenár oha.json stats
    printf '%-5s %-7s rps=%7.0f  p50=%7.2f ms  p95=%7.2f ms  p99=%7.2f ms  ok=%s | %s\n' "$1" "$2" \
        "$(metric "$3" requests_per_sec)" "$(metric "$3" p50)" "$(metric "$3" p95)" "$(metric "$3" p99)" \
        "$(metric "$3" success_rate)" "$4"
}

# Zahriatie (JIT v Jave), potom meranie; docker stats sa číta v polovici merania, keď je appka pod záťažou.
measure() { # app scenár url cookie [ďalšie argumenty oha]
    local app="$1" name="$2" url="$3" cookie="$4"; shift 4
    local headers=(-H "Cookie: $cookie" -H 'X-Requested-With: XMLHttpRequest')
    oha -z "$WARMUP" "${headers[@]}" "$@" "$url" > /dev/null
    ( sleep 5; stats ) > "$WORK/stats" &
    oha -z "$DUR" "${headers[@]}" "$@" "$url" > "$WORK/oha.json"
    wait
    report "$app" "$name" "$WORK/oha.json" "$(cat "$WORK/stats")"
}

# Klienti bez cookie (boti, health-checky): každý POST založí novú lokálnu session. Opakované kolá ukážu,
# či appka s rastúcim počtom session spomaľuje.
churn() { # app url sso
    local app="$1" url="$2" round
    for round in 1 2 3 4; do
        oha -z 10s -m POST -T "$FORM_TYPE" -d "$SEARCH_FORM" -H "Cookie: SSO_ID=$3" "$url/blacklist/searchAction" > "$WORK/oha.json"
        report "$app" "churn#$round" "$WORK/oha.json" "$(stats)"
    done
}

for app in crm-web crm-web-rs sso-mock redis; do
    if [ -z "$(docker compose ps -q --status running "$app")" ]; then
        echo "FAIL  $app nebeží – spusti: docker compose up -d redis sso-mock crm-web crm-web-rs"
        exit 1
    fi
done

SSO="$(curl_in_net -D - -o /dev/null -X POST http://sso-mock:8090/sso/login \
    -d operator=spravca -d customer=C-1001 -d returnUrl=/crm/blacklist/search | cookie_from SSO_ID)"
# Platné lokálne session oboch appiek – inak by každý request zakladal novú (to meria až scenár churn).
JAVA_SESSION="$(curl_in_net -D - -o /dev/null -X POST -H "Cookie: SSO_ID=$SSO" -d "$SEARCH_FORM" \
    "$JAVA/blacklist/searchAction" | cookie_from CRM_SESSION)"
RUST_SESSION="$(curl_in_net -D - -o /dev/null -X POST -H "Cookie: SSO_ID=$SSO" -d "$SEARCH_FORM" \
    "$RUST/blacklist/searchAction" | cookie_from CRM_RS_SESSION)"
if [ -z "$SSO" ] || [ -z "$JAVA_SESSION" ] || [ -z "$RUST_SESSION" ]; then
    echo "FAIL  prihlásenie (SSO_ID='$SSO', CRM_SESSION='$JAVA_SESSION', CRM_RS_SESSION='$RUST_SESSION')"
    exit 1
fi
JAVA_COOKIE="SSO_ID=$SSO; CRM_SESSION=$JAVA_SESSION"
RUST_COOKIE="SSO_ID=$SSO; CRM_RS_SESSION=$RUST_SESSION"

echo "== súbežnosť $CONC, zahriatie $WARMUP, meranie $DUR, scenáre: $SCENARIOS"
for scenario in $SCENARIOS; do
    post=()
    case "$scenario" in
        css)    path="/css/crm.css" ;;
        search) path="/blacklist/search" ;;
        ajax)   path="/blacklist/resource/refreshCreateConditionTypes?stateReason=DEBT" ;;
        post)   path="/blacklist/searchAction"; post=(-m POST -T "$FORM_TYPE" -d "$SEARCH_FORM") ;;
        churn)  churn java "$JAVA" "$SSO"; churn rust "$RUST" "$SSO"; continue ;;
        *)      echo "FAIL  neznámy scenár: $scenario"; exit 1 ;;
    esac
    measure java "$scenario" "$JAVA$path" "$JAVA_COOKIE" "${post[@]}"
    measure rust "$scenario" "$RUST$path" "$RUST_COOKIE" "${post[@]}"
done
