import test from 'node:test';
import assert from 'node:assert/strict';
import { fetchPronunciations, renderPronunciationEditor, savePronunciations } from '../src/producer-pronunciations.ts';

test('loads and safely renders pronunciation fixes', async () => {
  const draft = await fetchPronunciations('http://radio', 'token', async () => new Response(
    JSON.stringify({ schemaVersion: 1, text: 'AI=<script>', revision: 2 }), { status: 200 }));
  const html = renderPronunciationEditor(draft);
  assert.match(html, /AI=&lt;script&gt;/);
  assert.match(html, /Revision 2/);
});

test('saves the editable term list with bearer authorization', async () => {
  let request: RequestInit | undefined;
  const saved = await savePronunciations('http://radio', 'producer', 'SQL=sequel', async (_url, init) => {
    request = init;
    return new Response(JSON.stringify({ schemaVersion: 1, text: 'SQL=sequel', revision: 1 }), { status: 200 });
  });
  assert.equal(saved.revision, 1);
  assert.equal(request?.method, 'PUT');
  assert.equal((request?.headers as Record<string, string>).Authorization, 'Bearer producer');
});

test('explains malformed pronunciation input', async () => {
  await assert.rejects(() => savePronunciations('http://radio', 'token', 'bad', async () => new Response('', { status: 400 })),
    /unique term=spoken/);
});
