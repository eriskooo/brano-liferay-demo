# Build kontext = koreň repa (docker-compose: context ..). Rust appka berie texty aj statické súbory priamo
# z crm-web – jeden zdroj, nič sa nekopíruje do repa. Čo smie ísť do kontextu, určuje Dockerfile.dockerignore.
FROM rust:1.98.1-slim-bookworm AS build
WORKDIR /repo/crm-web-rs
# Najprv len závislosti: vrstva sa použije znova, kým sa nezmení Cargo.toml/Cargo.lock (build závislostí trvá minúty).
COPY crm-web-rs/Cargo.toml crm-web-rs/Cargo.lock ./
RUN mkdir src && echo 'fn main() {}' > src/main.rs && touch src/lib.rs \
    && cargo build --release --locked && rm -rf src
COPY crm-web/src/main/resources/messages.properties /repo/crm-web/src/main/resources/messages.properties
COPY crm-web-rs/ ./
# touch: skopírované zdrojáky môžu mať starší čas než dummy build – cargo by ich inak nepreložil.
RUN touch src/main.rs src/lib.rs && cargo build --release --locked

# Distroless: binárka z OS potrebuje len glibc a libgcc_s (ldd), bez shellu a balíčkovača – image ~32 MB namiesto ~84 MB
# s debian:bookworm-slim. Rovnaký Debian 12 ako build stage (zhodná glibc). Digest pinnutý – tag nemá verziu.
# Pre ladenie je variant :debug-nonroot s busybox shellom.
FROM gcr.io/distroless/cc-debian12:nonroot@sha256:9dac0a79194e45a7da0158a9c6da57b217585af0786db3845d1f0ec1a0dd182f
WORKDIR /app
COPY --from=build /repo/crm-web-rs/target/release/crm-web-rs /app/crm-web-rs
COPY crm-web/src/main/resources/static /app/static
ENV CRM_STATIC_DIR=/app/static
# Používateľ nonroot (65532) z base image.
EXPOSE 8080
ENTRYPOINT ["/app/crm-web-rs"]
