import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chapterSeconds, fetchChapters, parseChapters, renderChapters } from '../src/chapters.ts';

const payload = { schemaVersion: 1, chapters: [
  { title: 'Headlines', startMillis: 0 },
  { title: 'Local <report>', startMillis: 65_000 },
] };

test('parses ordered chapters and rejects malformed offsets', () => {
  assert.equal(parseChapters(payload).length, 2);
  assert.throws(() => parseChapters({ schemaVersion: 1, chapters: [
    { title: 'Late', startMillis: 10 }, { title: 'Earlier', startMillis: 5 },
  ] }), /unexpected chapter/);
});

test('loads the streamer chapter endpoint', async () => {
  let requested = '';
  const chapters = await fetchChapters('http://stream', 'edition-1', async (url) => {
    requested = String(url);
    return new Response(JSON.stringify(payload));
  });
  assert.equal(requested, 'http://stream/editions/edition-1/chapters');
  assert.equal(chapterSeconds(chapters[1]), 65);
});

test('renders safe seek controls with readable offsets', () => {
  const html = renderChapters(parseChapters(payload));
  assert.match(html, /data-seek-millis="65000"/);
  assert.match(html, /1:05/);
  assert.match(html, /Local &lt;report&gt;/);
  assert.equal(renderChapters([]), '');
});
