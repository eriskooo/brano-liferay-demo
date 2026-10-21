#!/usr/bin/env bash
# Znalostná báza v grafe: up | extract | check | load | guide | down (PowerShell dvojča: kb.ps1).
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE="docker compose -f kb-graph/docker-compose.yml"
JAR=kb-graph/extractor/target/kb-graph-extractor.jar

usage() {
    echo "Použitie: bash kb-graph/kb.sh up|extract|check|load|guide|down"
    exit 64
}

# JAR sa prebuilduje, len keď chýba alebo sú zdrojáky novšie – opakovaný load je potom rýchly.
build_jar() {
    if [ ! -f "$JAR" ] || [ -n "$(find kb-graph/extractor/src kb-graph/extractor/pom.xml -newer "$JAR" -print -quit)" ]; then
        mvn -q -B -f kb-graph/extractor/pom.xml package -DskipTests
    fi
}

run_java() {
    build_jar
    java -Dstdout.encoding=UTF-8 -jar "$JAR" "$@" --repo .
}

[ $# -eq 1 ] || usage
case "$1" in
    up)
        $COMPOSE up -d --wait
        echo "Neo4j Browser: http://localhost:7474 (neo4j / kbdemo123), guide: :play http://localhost:7475/kb.html"
        ;;
    extract|check|guide)
        run_java "$1"
        ;;
    load)
        run_java load
        run_java guide
        ;;
    down)
        $COMPOSE down
        ;;
    *)
        usage
        ;;
esac
