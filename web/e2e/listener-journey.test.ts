import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, type ChildProcess } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { readConfig } from '../src/config.ts';
import { fetchEditions, renderEditionList } from '../src/editions.ts';
import { renderPlayer } from '../src/player.ts';

const root = fileURLToPath(new URL('../../', import.meta.url));
const classes = `${root}radio-api/target/classes`;
let api: ChildProcess;
let apiBase = '';

before(async () => {
  assert.ok(existsSync(classes), 'build the Java API first: mvn -B -ntp -DskipTests package');
  api = spawn('java', ['-cp', classes, 'ro.bitboy.dailysignals.DailySignals', '0'], { stdio: ['ignore', 'pipe', 'inherit'] });
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
