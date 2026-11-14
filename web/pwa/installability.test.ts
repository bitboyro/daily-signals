import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { cacheEditionForOffline, registerServiceWorker } from '../src/pwa.ts';

const read = (path: string) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

test('manifest has a complete install contract', () => {
  const manifest = JSON.parse(read('public/manifest.webmanifest'));
  assert.equal(manifest.name, 'Daily Signals Public Radio');
  assert.equal(manifest.start_url, '/');
  assert.equal(manifest.scope, '/');
  assert.equal(manifest.display, 'standalone');
  assert.deepEqual(manifest.icons.map((icon: { sizes: string }) => icon.sizes), ['192x192', '512x512']);
  for (const icon of manifest.icons) assert.ok(read(`public${icon.src}`).includes('<svg'));
});

test('listener advertises the manifest and registers the root service worker', async () => {
  const index = read('public/index.html');
  assert.match(index, /rel="manifest" href="\/manifest\.webmanifest"/);
  assert.match(index, /name="theme-color"/);
  let script = '';
  let scope = '';
  const registration = {} as ServiceWorkerRegistration;
  const navigator = { serviceWorker: { register: async (nextScript: string, options?: RegistrationOptions) => {
    script = nextScript; scope = options?.scope ?? ''; return registration;
  } } } as unknown as Pick<Navigator, 'serviceWorker'>;
  assert.equal(await registerServiceWorker(navigator), registration);
  assert.equal(script, '/service-worker.js');
  assert.equal(scope, '/');
  assert.equal(await registerServiceWorker({}), undefined);
});

test('service worker owns a versioned shell lifecycle', () => {
  const worker = read('public/service-worker.js');
  assert.match(worker, /daily-signals-shell-v2/);
  for (const event of ['install', 'activate', 'fetch']) assert.match(worker, new RegExp(`addEventListener\\('${event}'`));
  for (const asset of ['/', '/styles.css', '/runtime-config.js', '/manifest.webmanifest']) assert.ok(worker.includes(`'${asset}'`));
  for (const module of ['chapters', 'config', 'editions', 'main', 'playback-position', 'player', 'pwa', 'schema']) {
    assert.match(worker, new RegExp(`(?:'|, )${module}(?:'|,)`));
  }
});

test('today\'s manifest and audio are handed to the active worker', async () => {
  let message: Record<string, string> | undefined;
  const active = { postMessage: (value: Record<string, string>) => { message = value; } } as unknown as ServiceWorker;
  const navigator = { serviceWorker: { ready: Promise.resolve({ active }), controller: null } } as unknown as Pick<Navigator, 'serviceWorker'>;
  assert.equal(await cacheEditionForOffline(
    { apiBase: 'https://radio.example', streamBase: 'https://radio.example' },
    { editionId: 'morning-2026-11-14', airDate: '2026-11-14', title: 'Morning', audioPath: '/editions/morning-2026-11-14/audio' },
    navigator,
  ), true);
  assert.deepEqual(message, {
    type: 'CACHE_EDITION',
    editionId: 'morning-2026-11-14',
    edition: { editionId: 'morning-2026-11-14', airDate: '2026-11-14', title: 'Morning', audioPath: '/editions/morning-2026-11-14/audio' },
    manifestUrl: 'https://radio.example/editions/morning-2026-11-14/manifest',
    chaptersUrl: 'https://radio.example/editions/morning-2026-11-14/chapters',
    audioUrl: 'https://radio.example/editions/morning-2026-11-14/audio',
  });
  assert.equal(await cacheEditionForOffline(
    { apiBase: '', streamBase: '' },
    { editionId: 'demo', airDate: '2026-11-14', title: 'Demo', audioPath: '/editions/demo/audio' },
    {},
  ), false);
});

test('service worker replaces the retained edition with same-origin manifest and audio', () => {
  const worker = read('public/service-worker.js');
  assert.match(worker, /daily-signals-edition-v1/);
  assert.match(worker, /addEventListener\('message'/);
  assert.match(worker, /CACHE_EDITION/);
  assert.match(worker, /offline edition must be same-origin/);
  assert.match(worker, /cache\.delete/);
  assert.match(worker, /cache\.put/);
  assert.match(worker, /'\/api\/editions'/);
  assert.match(worker, /schemaVersion: 2/);
});

test('service worker retains the loaded listener graph without caching producer data', () => {
  const worker = read('public/service-worker.js');
  assert.doesNotMatch(worker, /\/api\/producer/);
  assert.match(worker, /caches\.match\(event\.request/);
  assert.match(worker, /event\.request\.mode === 'navigate'/);
});
