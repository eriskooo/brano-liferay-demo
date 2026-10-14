#!/usr/bin/env bash
# Testy Rust appky v Dockeri (lokálny Rust netreba) – náhrada `mvn verify` + Testcontainers z Java verzie.
# Dočasný Redis na vlastnej sieti; cargo registry a target/ sú vo volumoch, opakovaný beh nič nesťahuje.
# Argumenty idú do `cargo test` (napr. bash crm-web-rs/test.sh redis_store).
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash by inak prepisoval cesty v -v a -w
ROOT="$(cd "$(dirname "$0")/.." && (pwd -W 2>/dev/null || pwd))"
RUST_IMAGE="rust:1.98.1-slim-bookworm"
NET="crm-web-rs-test-$$"
REDIS="crm-web-rs-test-redis-$$"

cleanup() {
    docker rm -f "$REDIS" > /dev/null 2>&1 || true
    docker network rm "$NET" > /dev/null 2>&1 || true
}
trap cleanup EXIT

docker network create "$NET" > /dev/null
docker run -d --name "$REDIS" --network "$NET" redis:7.4-alpine > /dev/null
for _ in $(seq 1 50); do
    docker exec "$REDIS" redis-cli ping 2> /dev/null | grep -q PONG && break
    sleep 0.2
done

docker run --rm --network "$NET" \
    -e REDIS_URL="redis://$REDIS:6379/" -e CARGO_TARGET_DIR=/cargo-target \
    -v crm-web-rs-cargo:/usr/local/cargo/registry -v crm-web-rs-target:/cargo-target \
    -v "$ROOT/crm-web-rs:/repo/crm-web-rs" \
    -v "$ROOT/crm-web/src/main/resources:/repo/crm-web/src/main/resources:ro" \
    -w /repo/crm-web-rs "$RUST_IMAGE" cargo test "$@"
