import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fetchEditions, formatAirDate, parseEditionList, renderDailyEdition, renderEditionList, renderSchedulePreview } from '../src/editions.ts';

const read = (name: string) => JSON.parse(readFileSync(new URL(`../../contracts/${name}`, import.meta.url), 'utf8'));
const contract = read('edition-list-v2.json');

test('parses the shared listener contract', () => {
  const editions = parseEditionList(contract);
  assert.equal(editions.length, 1);
  assert.equal(editions[0].editionId, 'demo-2026-01-15');
});

test('still accepts the v1 array from older deployments', () => {
  const legacy = parseEditionList(read('edition-list-v1.json'));
  assert.deepEqual(legacy.map((e) => e.editionId), parseEditionList(contract).map((e) => e.editionId));
});

test('rejects unknown schema versions', () => {
  assert.throws(() => parseEditionList({ schemaVersion: 3, editions: [] }), /unsupported schema version: 3/);
  assert.throws(() => parseEditionList({ schemaVersion: 2 }), /missing editions/);
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
  assert.match(renderEditionList([]), /next scheduled broadcast/);
});

test('renders a station-local schedule preview', () => {
  assert.match(renderSchedulePreview({ airDate: '2026-01-16', stationTime: '08:30', zone: 'Europe/Bucharest' }), /08:30 Europe\/Bucharest/);
  assert.throws(() => renderSchedulePreview({ airDate: 'today', stationTime: '8:30', zone: '' }), /invalid schedule/);
});

test('renders the selected daily edition as live context', () => {
  assert.match(renderDailyEdition({ editionId: 'today', airDate: '2026-01-16', title: '<Morning>', audioPath: '/today' }), /aria-live="polite"/);
  assert.match(renderDailyEdition({ editionId: 'today', airDate: '2026-01-16', title: '<Morning>', audioPath: '/today' }), /&lt;Morning&gt;/);
});

test('formats air dates with an explicit locale and stable timezone', () => {
  assert.equal(formatAirDate('2026-01-16', 'en-GB'), '16 January 2026');
  assert.match(formatAirDate('2026-01-16', 'ro-RO'), /16 ianuarie 2026/);
  assert.throws(() => formatAirDate('today'), /invalid air date/);
});
