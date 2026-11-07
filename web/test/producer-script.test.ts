import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fetchScript, renderScriptEditor, saveScript } from '../src/producer-script.ts';

test('loads and saves the authenticated script draft', async () => {
  const calls: RequestInit[] = [];
  const fetchImpl = async (_input: unknown, init?: RequestInit) => {
    calls.push(init!);
    return new Response(JSON.stringify({ schemaVersion: 1, text: 'Today’s script.', revision: calls.length - 1 }));
  };
  assert.equal((await fetchScript('http://api.test', 'editor-token', fetchImpl as typeof fetch)).revision, 0);
  assert.equal((await saveScript('http://api.test', 'editor-token', 'Today’s script.', fetchImpl as typeof fetch)).revision, 1);
  assert.equal((calls[0].headers as Record<string, string>).Authorization, 'Bearer editor-token');
  assert.equal(calls[1].method, 'PUT');
  assert.equal(calls[1].body, 'Today’s script.');
});

test('renders a bounded editor and surfaces authorization failures', async () => {
  assert.match(renderScriptEditor({ text: '<spoken>', revision: 2 }), /&lt;spoken&gt;/);
  assert.match(renderScriptEditor({ text: 'draft', revision: 2 }), /maxlength="20000"/);
  const denied = async () => new Response(null, { status: 403 });
  await assert.rejects(fetchScript('http://api.test', 'listener', denied as typeof fetch), /cannot edit/);
  await assert.rejects(saveScript('http://api.test', '', '', denied as typeof fetch), /cannot edit/);
});
