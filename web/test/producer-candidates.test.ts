import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fetchTodaysCandidates, renderTodaysCandidates } from '../src/producer-candidates.ts';

const item = (storyId: string, decision: 'PENDING' | 'APPROVED' | 'REJECTED') => ({ storyId,
  headline: `${storyId} <headline>`, source: 'City desk', decision,
  review: { reviewer: null, reviewedAt: null, revision: 0 } });

test('loads the current API candidate envelope and excludes rejected stories', async () => {
  const fetchImpl = async () => new Response(JSON.stringify({ schemaVersion: 3,
    items: [item('pending', 'PENDING'), item('approved', 'APPROVED'), item('rejected', 'REJECTED')] }));
  assert.deepEqual((await fetchTodaysCandidates('http://api.test', fetchImpl as typeof fetch))
    .map((candidate) => candidate.storyId), ['pending', 'approved']);
});

test('renders accessible candidate status without trusting API markup', () => {
  const html = renderTodaysCandidates([item('approved', 'APPROVED'), item('pending', 'PENDING')]);
  assert.match(html, /Today’s candidates <span>2<\/span>/);
  assert.match(html, /approved &lt;headline&gt;/);
  assert.match(html, /data-decision="APPROVED"/);
  assert.match(html, /pending/);
  assert.equal(renderTodaysCandidates([]), '<p>No usable candidates have arrived today.</p>');
});
