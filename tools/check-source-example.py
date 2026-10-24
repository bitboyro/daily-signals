#!/usr/bin/env python3
"""Check that documented source configuration agrees with source registry policy."""
import json
import pathlib
import re
from urllib.parse import urlparse

root=pathlib.Path(__file__).resolve().parents[1]
config=json.loads((root/'examples/sources.json').read_text())
assert isinstance(config.get('sources'),list) and config['sources']
seen=set()
for source in config['sources']:
    assert set(source)=={'id','name','url','priority'}
    assert re.fullmatch(r'[a-z][a-z0-9-]*',source['id'])
    assert source['id'] not in seen
    seen.add(source['id'])
    assert source['name'].strip()
    url=urlparse(source['url'])
    assert url.scheme=='https' and url.hostname, 'source URL must be HTTPS'
    assert isinstance(source['priority'],int) and source['priority']>=0
print(f'{len(seen)} documented source configuration passed')
