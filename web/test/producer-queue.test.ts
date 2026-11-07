import { test } from 'node:test';
import assert from 'node:assert/strict';
import { filterQueue, parseQueue, pendingCount, renderQueue, submitDecision, type QueueItem } from '../src/producer-queue.ts';

const items: QueueItem[] = [
  { storyId: 'story-1', headline: 'Rail <maintenance>', source: 'transit-desk', decision: 'PENDING', review: { reviewer: null, reviewedAt: null, revision: 0 } },
  { storyId: 'story-2', headline: 'Library hours', source: 'city-desk', decision: 'APPROVED', review: { reviewer: 'EDITOR', reviewedAt: '2026-11-07T10:30:00Z', revision: 1 } },
  { storyId: 'story-3', headline: 'Bridge works', source: 'city-desk', decision: 'PENDING', review: { reviewer: null, reviewedAt: null, revision: 0 } },
];

test('counts only pending decisions', () => {
  assert.equal(pendingCount(items), 2);
});

test('queue table has a caption, column headers and escaped headlines', () => {
  const html = renderQueue(items);
  assert.match(html, /<caption>Review queue: 2 pending<\/caption>/);
  assert.equal(html.match(/<th scope="col">/g)?.length, 5);
  assert.match(html, /Rail &lt;maintenance&gt;/);
  assert.match(html, /data-story="story-2".*decision-approved/);
});

test('parses the versioned review queue envelope', () => {
  assert.deepEqual(parseQueue({ schemaVersion: 3, items }), items);
  assert.throws(() => parseQueue(items), /versioned response/);
  assert.throws(() => parseQueue({ schemaVersion: 3, items: [{ ...items[0], decision: 'MAYBE' }] }), /review queue/);
  assert.throws(() => parseQueue({ schemaVersion: 3, items: [{ ...items[0], review: { reviewer: 'EDITOR', reviewedAt: 'never', revision: 1 } }] }), /review queue/);
});

test('renders persisted reviewer, time and revision', () => {
  const html = renderQueue(items);
  assert.match(html, /EDITOR · <time datetime="2026-11-07T10:30:00Z">2026-11-07T10:30:00Z<\/time> · r1/);
  assert.match(html, /Not reviewed/);
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

test('returns the server-issued decision receipt', async () => {
  const accepted = async () => new Response(JSON.stringify({ storyId: 'story-1', decision: 'APPROVED' }), { status: 200 });
  await assert.doesNotReject(submitDecision('http://api.invalid', 'story-1', 'APPROVED', 'secret-token', accepted as typeof fetch));
  const malformed = async () => new Response(JSON.stringify({ storyId: 'another-story', decision: 'APPROVED' }), { status: 200 });
  await assert.rejects(submitDecision('http://api.invalid', 'story-1', 'APPROVED', 'secret-token', malformed as typeof fetch), /unexpected decision response/);
});
