import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { registerServiceWorker } from '../src/pwa.ts';

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
  assert.match(worker, /daily-signals-shell-v1/);
  for (const event of ['install', 'activate', 'fetch']) assert.match(worker, new RegExp(`addEventListener\\('${event}'`));
  for (const asset of ['/', '/styles.css', '/runtime-config.js', '/app/main.js', '/manifest.webmanifest']) assert.ok(worker.includes(`'${asset}'`));
});
