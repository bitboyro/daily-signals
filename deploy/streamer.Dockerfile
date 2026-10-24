# Rust streamer serving one published manifest. Build context: repository root.
FROM rust:1.99.1-slim-bookworm AS build
WORKDIR /src
COPY contracts/ contracts/
COPY streamer/ streamer/
RUN cargo build --release --locked --manifest-path streamer/Cargo.toml

FROM debian:bookworm-20261020-slim
COPY --from=build /src/streamer/target/release/daily-signals-streamer /usr/local/bin/daily-signals-streamer
USER 10001
EXPOSE 8152
ENTRYPOINT ["/usr/local/bin/daily-signals-streamer"]
CMD ["8152", "/srv/published/manifest.json", "/srv/published"]
