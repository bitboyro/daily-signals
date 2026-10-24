import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { renderSourceEditor, validateSources, type SourceConfig } from '../src/source-editor.ts';

const example: { sources: SourceConfig[] } = JSON.parse(readFileSync(new URL('../../examples/sources.json', import.meta.url), 'utf8'));

test('documented example source configuration is valid', () => {
  assert.deepEqual(validateSources(example.sources), []);
});

test('reports the same problems as the source registry', () => {
  const problems = validateSources([
    { id: 'Desk', name: ' ', url: 'http://example.invalid/feed', priority: -1 },
    { id: 'desk', name: 'Desk', url: 'https://example.invalid/a', priority: 0 },
    { id: 'desk', name: 'Desk again', url: 'https://example.invalid/b', priority: 1.5 },
  ]);
  assert.deepEqual(problems, [
    'source 1: id must be lowercase letters, digits or dashes',
    'source 1: name is required',
    'source 1: URL must be HTTPS',
    'source 1: priority must be a nonnegative integer',
    'source 3: duplicate id desk',
    'source 3: priority must be a nonnegative integer',
  ]);
});

test('every editor input has a label and values are escaped', () => {
  const html = renderSourceEditor([{ id: 'desk', name: 'Desk "A"', url: 'https://example.invalid/a', priority: 2 }]);
  for (const name of ['id', 'name', 'url', 'priority']) {
    assert.match(html, new RegExp(`<label for="source-0-${name}">`));
    assert.match(html, new RegExp(`<input id="source-0-${name}"`));
  }
  assert.match(html, /value="Desk &quot;A&quot;"/);
  assert.match(html, /role="status" aria-live="polite"/);
});
