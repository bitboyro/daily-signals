import test from 'node:test';
import assert from 'node:assert/strict';
import { fetchEditionState, transitionEdition } from '../src/producer-edition.ts';

test('reads and transitions the persisted producer edition', async () => {
  const calls: RequestInit[] = [];
  const fetchImpl = async (_url: unknown, init?: RequestInit) => {
    calls.push(init!);
    const status = calls.length === 1 ? 'DRAFT' : 'READY';
    return new Response(JSON.stringify({ schemaVersion: 1, editionId: 'producer-today', status, revision: calls.length - 1 }));
  };
  assert.equal((await fetchEditionState('http://radio', 'producer', fetchImpl as typeof fetch)).status, 'DRAFT');
  assert.equal((await transitionEdition('http://radio', 'producer', 'APPROVE', fetchImpl as typeof fetch)).status, 'READY');
  assert.equal(calls[1].method, 'POST');
  assert.equal(calls[1].body, 'APPROVE');
});

test('rejects invalid state and reports transition conflicts', async () => {
  await assert.rejects(fetchEditionState('http://radio', 'token', async () =>
    new Response(JSON.stringify({ schemaVersion: 1, status: 'BROKEN' }))), /unexpected edition/);
  await assert.rejects(transitionEdition('http://radio', 'token', 'PUBLISH', async () =>
    new Response('', { status: 409 })), /not ready/);
});
