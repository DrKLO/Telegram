ARG RUST_VERSION=1.98.0

FROM --platform=linux/amd64 debian:bookworm-slim

ARG RUST_VERSION
ENV DEBIAN_FRONTEND=noninteractive \
    RUSTUP_HOME=/opt/rustup \
    CARGO_HOME=/opt/cargo \
    PATH=/opt/cargo/bin:/opt/rustup/bin:$PATH \
    TZ=UTC \
    LC_ALL=C \
    SOURCE_DATE_EPOCH=1 \
    CARGO_INCREMENTAL=0

RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      build-essential \
    && rm -rf /var/lib/apt/lists/*

RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | \
      sh -s -- -y \
        --profile minimal \
        --default-toolchain "$RUST_VERSION" \
        --no-modify-path \
    && chmod -R a+rX /opt/rustup /opt/cargo

RUN rustup target add \
      aarch64-linux-android \
      armv7-linux-androideabi \
      i686-linux-android \
      x86_64-linux-android

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

WORKDIR /work
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]