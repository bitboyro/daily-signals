import { test } from 'node:test';
import assert from 'node:assert/strict';
import { filterQueue, parseQueue, pendingCount, renderQueue, submitDecision, type QueueItem } from '../src/producer-queue.ts';

const items: QueueItem[] = [
  { storyId: 'story-1', headline: 'Rail <maintenance>', source: 'transit-desk', decision: 'PENDING' },
  { storyId: 'story-2', headline: 'Library hours', source: 'city-desk', decision: 'APPROVED' },
  { storyId: 'story-3', headline: 'Bridge works', source: 'city-desk', decision: 'PENDING' },
];

test('counts only pending decisions', () => {
  assert.equal(pendingCount(items), 2);
});

test('queue table has a caption, column headers and escaped headlines', () => {
  const html = renderQueue(items);
  assert.match(html, /<caption>Review queue: 2 pending<\/caption>/);
  assert.equal(html.match(/<th scope="col">/g)?.length, 4);
  assert.match(html, /Rail &lt;maintenance&gt;/);
  assert.match(html, /data-story="story-2".*decision-approved/);
});

test('parses the versioned review queue envelope', () => {
  assert.deepEqual(parseQueue({ schemaVersion: 2, items }), items);
  assert.throws(() => parseQueue(items), /versioned response/);
  assert.throws(() => parseQueue({ schemaVersion: 2, items: [{ ...items[0], decision: 'MAYBE' }] }), /review queue/);
});

test('only pending stories get labelled approval controls', () => {
  const html = renderQueue(items);
  assert.equal(html.match(/data-action="APPROVED"/g)?.length, 2);
  assert.match(html, /aria-label="Approve Rail &lt;maintenance&gt;"/);
  assert.doesNotMatch(html, /aria-label="Approve Library hours"/);
});

test('filters the queue by decision and escaped free-text fields', () => {
  assert.deepEqual(filterQueue(items, 'PENDING').map((item) => item.storyId), ['story-1', 'story-3']);
  assert.deepEqual(filterQueue(items, 'ALL', 'CITY').map((item) => item.storyId), ['story-2', 'story-3']);
  const html = renderQueue(items, 'PENDING', 'bridge');
  assert.match(html, /aria-label="Filter review queue"/);
  assert.match(html, /option value="PENDING" selected/);
  assert.match(html, /Bridge works/);
  assert.doesNotMatch(html, /Library hours/);
});

test('decisions send the reviewer token and surface denials', async () => {
  const seen: RequestInit[] = [];
  const answer = (status: number) => async (_url: unknown, init?: RequestInit) => { seen.push(init!); return new Response(null, { status }); };
  await assert.rejects(submitDecision('http://api.invalid', 'story 1', 'APPROVED', 'secret-token', answer(403) as typeof fetch), /cannot review/);
  await assert.rejects(submitDecision('http://api.invalid', 'story 1', 'APPROVED', '', answer(401) as typeof fetch), /reviewer token/);
  const headers = seen[0].headers as Record<string, string>;
  assert.equal(headers.Authorization, 'Bearer secret-token');
  assert.equal(headers['X-Daily-Signals-Role'], undefined);
  assert.equal(seen[0].body, 'APPROVED');
});
