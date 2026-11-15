FROM python:3.14.0-slim-bookworm
WORKDIR /app
COPY deploy/object_store.py /app/object_store.py
COPY contracts/publication-manifest-v2.json /seed/manifest.json
COPY radio-api/src/main/resources/fixtures/demo-edition.wav /seed/audio.wav
RUN mkdir -p /data && chown 10001:10001 /data
USER 10001
EXPOSE 9000
HEALTHCHECK --interval=5s --timeout=3s --retries=10 CMD ["python3", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:9000/health')"]
ENTRYPOINT ["python3", "/app/object_store.py"]
CMD ["--root", "/data", "--seed-manifest", "/seed/manifest.json", "--seed-audio", "/seed/audio.wav"]
