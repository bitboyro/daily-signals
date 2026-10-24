#!/usr/bin/env python3
"""Check the declared reactor DAG and source ownership after the E1 split."""
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
EXPECTED = {
    'radio-ingestion': set(),
    'radio-editorial': {'radio-ingestion'},
    'radio-edition': {'radio-editorial'},
    'radio-persistence': {'radio-edition'},
    'radio-api': {'radio-ingestion', 'radio-editorial', 'radio-edition'},
}
reactor = ET.parse(ROOT / 'pom.xml').getroot()
modules = {e.text for e in reactor.findall('m:modules/m:module', NS)}
assert modules == set(EXPECTED), (modules, set(EXPECTED))
seen = {}
for module, deps in EXPECTED.items():
    pom = ET.parse(ROOT / module / 'pom.xml').getroot()
    actual = {e.text for e in pom.findall('m:dependencies/m:dependency/m:artifactId', NS) if e.text in EXPECTED}
    assert actual == deps, (module, actual, deps)
    for source in (ROOT / module / 'src/main/java').rglob('*.java'):
        assert source.name not in seen, (source, seen.get(source.name))
        seen[source.name] = str(source)
assert {'SourceSnapshotParser.java', 'CandidateReview.java', 'EditionAssembler.java'} <= seen.keys()
print('Reactor ownership and dependency DAG valid:', len(seen), 'source files')
