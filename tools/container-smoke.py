#!/usr/bin/env python3
"""Check the container descriptors and start both services the way the images would.

Default mode runs the image entrypoints on the host: container paths are mapped to their
build outputs or compose volume sources and bind addresses become loopback. With --docker
it builds and starts deploy/compose.yaml instead. Both modes probe startup and playback.
"""
import json
import os
import re
import socket
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEPLOY = ROOT / 'deploy'
MODULES = ('radio-api', 'radio-edition', 'radio-editorial', 'radio-ingestion')
# Compose volume sources and image build outputs, most specific first.
HOST_PATHS = [
    ('/srv/published/manifest.json', ROOT / 'contracts/publication-manifest-v2.json'),
    ('/srv/published', ROOT / 'radio-api/src/main/resources'),
    ('/app/web', ROOT / 'web/dist'),
    ('/usr/local/bin/daily-signals-streamer', ROOT / 'streamer/target/debug/daily-signals-streamer'),
    *[(f'/app/classes/{m}', ROOT / m / 'target/classes') for m in MODULES],
]


def descriptor(name):
    text = (DEPLOY / name).read_text()
    stages = [line.split()[1] for line in text.splitlines() if line.startswith('FROM ')]
    for image in stages:
        assert re.search(r':\S*(\d+\.\d+|\d{8})', image) and 'latest' not in image, f'{name}: unpinned base {image}'
    final = text.split('\nFROM ')[-1]
    user = re.search(r'^USER (\S+)', final, re.M)
    assert user and user.group(1) not in ('root', '0'), f'{name}: runtime stage must not run as root'
    entrypoint = json.loads(re.search(r'^ENTRYPOINT (\[.*\])$', final, re.M).group(1))
    command = json.loads(re.search(r'^CMD (\[.*\])$', final, re.M).group(1))
    expose = re.search(r'^EXPOSE (\d+)$', final, re.M).group(1)
    assert command[0] == expose, f'{name}: CMD port {command[0]} differs from EXPOSE {expose}'
    return entrypoint, command


def runtime_env():
    env = {}
    for line in (DEPLOY / 'runtime.env').read_text().splitlines():
        if line.strip() and not line.startswith('#'):
            key, value = line.split('=', 1)
            env[key] = value
    return env


def host(value):
    def one(part):
        for container, local in HOST_PATHS:
            if part == container or part.startswith(container + '/'):
                return str(local) + part[len(container):]
        return part
    return ':'.join(one(part) for part in value.split(':')) if value.startswith('/') else value


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def wait(url):
    for _ in range(100):
        try:
            with urllib.request.urlopen(url, timeout=.3) as response:
                return response.status
        except Exception:
            time.sleep(.1)
    raise RuntimeError(f'{url} did not become ready')


def probe(api, stream):
    manifest = json.loads((ROOT / 'contracts/publication-manifest-v2.json').read_text())
    wait(f'{api}/health')
    wait(f'{stream}/health')
    with urllib.request.urlopen(f'{api}/') as page:
        assert b'<main id="content">' in page.read(), 'listener UI not served'
    with urllib.request.urlopen(f'{api}/api/editions') as editions:
        assert json.load(editions)['schemaVersion'] == 2
    audio = f'{stream}/editions/{manifest["editionId"]}/audio'
    with urllib.request.urlopen(audio) as response:
        body = response.read()
        assert len(body) == manifest['sizeBytes'] and response.headers['ETag'] == f'"{manifest["checksumSha256"]}"'
    with urllib.request.urlopen(urllib.request.Request(audio, headers={'Range': 'bytes=0-3'})) as response:
        assert response.status == 206 and response.read() == b'RIFF'


def host_run():
    env = runtime_env()
    compose = (DEPLOY / 'compose.yaml').read_text()
    for needed in ('dockerfile: deploy/api.Dockerfile', 'dockerfile: deploy/streamer.Dockerfile', 'env_file: runtime.env',
                   '../contracts/publication-manifest-v2.json:/srv/published/manifest.json:ro',
                   '../radio-api/src/main/resources/fixtures:/srv/published/fixtures:ro'):
        assert needed in compose, f'compose.yaml no longer declares {needed}'
    processes = []
    try:
        ports = {}
        for name in ('api', 'streamer'):
            entrypoint, command = descriptor(f'{name}.Dockerfile')
            ports[name] = free_port()
            argv = [host(arg) for arg in entrypoint + [str(ports[name])] + command[1:]]
            child_env = {**os.environ, **{k: host(v) for k, v in env.items()}}
            child_env.update(DAILY_SIGNALS_BIND_ADDRESS='127.0.0.1', STREAMER_BIND_ADDRESS='127.0.0.1')
            processes.append(subprocess.Popen(argv, env=child_env, stdout=subprocess.DEVNULL))
        time.sleep(.2)
        for process in processes:
            if process.poll() is not None:
                raise RuntimeError(f'{process.args[0]} exited during startup with {process.returncode}')
        probe(f'http://127.0.0.1:{ports["api"]}', f'http://127.0.0.1:{ports["streamer"]}')
    finally:
        for process in processes:
            process.terminate()
            try: process.wait(timeout=3)
            except subprocess.TimeoutExpired: process.kill()
    print('Container descriptors valid; host-run API and streamer started and played the published edition')


def docker_run():
    compose = ['docker', 'compose', '-f', str(DEPLOY / 'compose.yaml')]
    subprocess.run(compose + ['up', '-d', '--build'], check=True)
    try:
        probe('http://127.0.0.1:8151', 'http://127.0.0.1:8152')
    finally:
        subprocess.run(compose + ['logs'], check=False)
        subprocess.run(compose + ['down'], check=False)
    print('Compose images started and played the published edition')


if __name__ == '__main__':
    docker_run() if '--docker' in sys.argv[1:] else host_run()
