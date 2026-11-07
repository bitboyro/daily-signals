import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fetchSchedule, parseFetchSchedule, renderFetchSchedule } from '../src/fetch-schedule.ts';

const schedule = {
  schemaVersion: 1 as const,
  nextRun: '2026-01-16T04:15:00Z',
  zone: 'Europe/Bucharest',
  window: '06:15',
  sources: ['public-desk', 'world-wire'],
};

test('parses the versioned fetch schedule', () => {
  assert.deepEqual(parseFetchSchedule(schedule), schedule);
  assert.throws(() => parseFetchSchedule({ ...schedule, schemaVersion: 2 }), /unexpected fetch schedule/);
  assert.throws(() => parseFetchSchedule({ ...schedule, sources: [] }), /unexpected fetch schedule/);
  assert.throws(() => parseFetchSchedule({ ...schedule, nextRun: 'tomorrow' }), /unexpected fetch schedule/);
});

test('fetches the schedule from the API', async () => {
  let requested = '';
  const answer = async (input: string | URL | Request) => {
    requested = String(input);
    return new Response(JSON.stringify(schedule), { status: 200 });
  };
  assert.deepEqual(await fetchSchedule('http://api.test', answer as typeof fetch), schedule);
  assert.equal(requested, 'http://api.test/api/schedule');
  const unavailable = async () => new Response('', { status: 503 });
  await assert.rejects(fetchSchedule('http://api.test', unavailable as typeof fetch), /503/);
});

test('renders the next run in station-local time for producers', () => {
  const html = renderFetchSchedule(schedule, 'en-GB');
  assert.match(html, /Next source fetch/);
  assert.match(html, /16 Jan 2026/);
  assert.match(html, /06:15/);
  assert.match(html, /Europe\/Bucharest/);
  assert.match(html, /2 sources/);
  assert.match(html, /datetime="2026-01-16T04:15:00Z"/);
});
