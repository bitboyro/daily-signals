import assert from 'node:assert/strict';
import test from 'node:test';
import { fetchSetup, parseSetup, renderSetup, saveSetup, type StationSetup } from '../src/setup.ts';

const setup: StationSetup = {
  schemaVersion: 1, configured: false,
  sources: [{ id: 'public-desk', name: 'Public desk', url: 'https://news.example/public.xml', priority: 10 }],
  voice: 'fixture', zone: 'Europe/Bucharest', window: '06:15',
};

test('parses and renders the three guided setup steps', () => {
  assert.deepEqual(parseSetup(setup), setup);
  const html = renderSetup(setup);
  assert.match(html, /1\. News source/);
  assert.match(html, /2\. Station voice/);
  assert.match(html, /3\. Daily schedule/);
  assert.match(html, /docker compose --profile kokoro up --build/);
  assert.match(html, /Europe\/Bucharest/);
  assert.throws(() => parseSetup({ ...setup, voice: 'cloud' }), /unexpected setup/);
});

test('reads and writes setup through the API', async () => {
  const requests: Request[] = [];
  const answer = async (input: string | URL | Request, init?: RequestInit) => {
    requests.push(new Request(input, init));
    return new Response(JSON.stringify({ ...setup, configured: true }), { status: 200 });
  };
  assert.equal((await fetchSetup('http://api.test', answer as typeof fetch)).configured, true);
  assert.equal((await saveSetup('http://api.test', { ...setup, configured: true }, 'token', answer as typeof fetch)).configured, true);
  assert.equal(requests[0].url, 'http://api.test/api/setup');
  assert.equal(requests[1].method, 'PUT');
  assert.equal(requests[1].headers.get('Authorization'), 'Bearer token');
});
