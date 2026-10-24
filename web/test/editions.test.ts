import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fetchEditions, parseEditionList, renderEditionList } from '../src/editions.ts';

const contract = JSON.parse(readFileSync(new URL('../../contracts/edition-list-v1.json', import.meta.url), 'utf8'));

test('parses the shared listener contract', () => {
  const editions = parseEditionList(contract);
  assert.equal(editions.length, 1);
  assert.equal(editions[0].editionId, 'demo-2026-01-15');
});

test('lists newest editions first and escapes titles', () => {
  const html = renderEditionList(parseEditionList([
    { editionId: 'a', airDate: '2026-01-01', title: 'Older', audioPath: '/editions/a/audio' },
    { editionId: 'b', airDate: '2026-02-01', title: '<b>Newer</b>', audioPath: '/editions/b/audio' },
  ]));
  assert.ok(html.indexOf('2026-02-01') < html.indexOf('2026-01-01'));
  assert.ok(html.includes('&lt;b&gt;Newer&lt;/b&gt;'));
});

test('rejects malformed responses and reports unavailable APIs', async () => {
  assert.throws(() => parseEditionList({ editions: [] }));
  assert.throws(() => parseEditionList([{ editionId: 'x', airDate: 'today', title: 't', audioPath: '/a' }]));
  const unavailable = async () => new Response('', { status: 503 });
  await assert.rejects(fetchEditions('http://api.invalid', unavailable as typeof fetch), /503/);
});

test('renders an empty archive message', () => {
  assert.match(renderEditionList([]), /No editions/);
});
