import { test } from 'node:test';
import assert from 'node:assert/strict';
import { pendingCount, renderQueue, type QueueItem } from '../src/producer-queue.ts';

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
  assert.equal(html.match(/<th scope="col">/g)?.length, 3);
  assert.match(html, /Rail &lt;maintenance&gt;/);
  assert.match(html, /data-story="story-2".*decision-approved/);
});
