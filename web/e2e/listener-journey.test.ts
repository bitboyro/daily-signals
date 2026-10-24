import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, type ChildProcess } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { readConfig } from '../src/config.ts';
import { fetchEditions, renderEditionList } from '../src/editions.ts';
import { renderPlayer } from '../src/player.ts';
import { fetchQueue, renderQueue, submitDecision } from '../src/producer-queue.ts';

const root = fileURLToPath(new URL('../../', import.meta.url));
const classes = ['radio-api', 'radio-edition', 'radio-editorial', 'radio-ingestion']
  .map((module) => `${root}${module}/target/classes`).join(':');
// Generated per run; reviewer tokens are configuration, never committed values.
const producerToken = `producer-${randomUUID()}`;
const listenerToken = `listener-${randomUUID()}`;
const webRoot = fileURLToPath(new URL('../dist', import.meta.url));
let api: ChildProcess;
let apiBase = '';

before(async () => {
  assert.ok(classes.split(':').every((dir) => existsSync(dir)), 'build the Java API first: mvn -B -ntp -DskipTests package');
  assert.ok(existsSync(`${webRoot}/index.html`), 'build the listener first: npm run build');
  api = spawn('java', ['-cp', classes, 'ro.bitboy.dailysignals.DailySignals', '0'], {
    stdio: ['ignore', 'pipe', 'inherit'],
    env: {
      ...process.env,
      DAILY_SIGNALS_WEB_ROOT: webRoot,
      DAILY_SIGNALS_REVIEW_TOKENS: `${producerToken}=PRODUCER,${listenerToken}=LISTENER`,
    },
  });
  apiBase = await new Promise<string>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Java API did not start')), 15000);
    api.stdout!.on('data', (chunk: Buffer) => {
      const match = /listening on (http:\/\/\S+)/.exec(chunk.toString());
      if (match) { clearTimeout(timer); resolve(match[1]); }
    });
    api.on('exit', (code) => reject(new Error(`Java API exited with ${code}`)));
  });
});

after(() => { api?.kill(); });

test('the API serves the listener UI and its module', async () => {
  const page = await fetch(`${apiBase}/`);
  assert.equal(page.status, 200);
  const html = await page.text();
  assert.match(html, /<main id="content">/);
  const script = /<script type="module" src="([^"]+)"/.exec(html)?.[1];
  assert.equal(script, '/app/main.js');
  const module = await fetch(`${apiBase}${script}`);
  assert.equal(module.status, 200);
  assert.match(module.headers.get('content-type') ?? '', /javascript/);
  assert.match(await module.text(), /from "\.\/editions\.js"/);
});

test('listener browses the archive and plays the newest edition', async () => {
  const config = readConfig(apiBase);
  const editions = await fetchEditions(config.apiBase);
  assert.ok(editions.length > 0);
  assert.match(renderEditionList(editions), new RegExp(editions[0].editionId));

  const player = renderPlayer(config, editions[0]);
  const src = /<audio[^>]* src="([^"]+)"/.exec(player)?.[1];
  assert.ok(src, 'player exposes an audio source');
  const audio = await fetch(src);
  assert.equal(audio.status, 200);
  assert.equal(audio.headers.get('content-type'), 'audio/wav');
  const bytes = new Uint8Array(await audio.arrayBuffer());
  assert.equal(new TextDecoder().decode(bytes.slice(0, 4)), 'RIFF');
});

test('producer approves a pending story and the queue reflects it', async () => {
  const before = await fetchQueue(apiBase);
  const pending = before.find((item) => item.decision === 'PENDING');
  assert.ok(pending, 'fixture queue has a pending story');
  assert.match(renderQueue(before), new RegExp(`Approve ${pending.headline}`));

  await assert.rejects(submitDecision(apiBase, pending.storyId, 'APPROVED', ''), /reviewer token/);
  await assert.rejects(submitDecision(apiBase, pending.storyId, 'APPROVED', listenerToken), /cannot review/);
  await submitDecision(apiBase, pending.storyId, 'APPROVED', producerToken);
  const after = await fetchQueue(apiBase);
  assert.equal(after.find((item) => item.storyId === pending.storyId)?.decision, 'APPROVED');
});
