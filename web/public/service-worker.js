const CACHE = 'daily-signals-shell-v3';
const EDITION_CACHE = 'daily-signals-edition-v1';
const APP_MODULES = [
  'chapters', 'config', 'editions', 'fetch-schedule', 'html', 'main', 'playback-position', 'player',
  'producer-auth', 'producer-candidates', 'producer-edition', 'producer-pronunciations', 'producer-queue',
  'producer-script', 'producer-workspace', 'pwa', 'schema', 'source-editor',
].map((name) => `/app/${name}.js`);
const SHELL = ['/', '/styles.css', '/runtime-config.js', '/manifest.webmanifest', ...APP_MODULES];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(
    keys.filter((key) => key.startsWith('daily-signals-shell-') && key !== CACHE).map((key) => caches.delete(key)),
  )).then(() => self.clients.claim()));
});

async function cacheEdition(message) {
  if (!/^[a-z0-9-]+$/.test(message.editionId ?? '')) throw new Error('invalid edition id');
  if (message.edition?.editionId !== message.editionId) throw new Error('offline edition metadata mismatch');
  const urls = [message.manifestUrl, message.chaptersUrl, message.audioUrl].map((value) => {
    const url = new URL(value, self.location.origin);
    if (url.origin !== self.location.origin) throw new Error('offline edition must be same-origin');
    return url.href;
  });
  const responses = await Promise.all(urls.map((url) => fetch(url)));
  if (responses.some((response) => !response.ok)) throw new Error('offline edition unavailable');
  const cache = await caches.open(EDITION_CACHE);
  await Promise.all((await cache.keys()).map((request) => cache.delete(request)));
  await Promise.all(urls.map((url, index) => cache.put(url, responses[index])));
  await cache.put('/api/editions', new Response(JSON.stringify({
    schemaVersion: 2,
    editions: [message.edition],
  }), { headers: { 'Content-Type': 'application/json' } }));
}

self.addEventListener('message', (event) => {
  if (event.data?.type === 'CACHE_EDITION') event.waitUntil(cacheEdition(event.data));
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET' || new URL(event.request.url).origin !== self.location.origin) return;
  event.respondWith((async () => {
    const cached = await caches.match(event.request, { ignoreVary: true });
    if (cached) return cached;
    try {
      return await fetch(event.request);
    } catch (error) {
      if (event.request.mode === 'navigate') return caches.match('/');
      throw error;
    }
  })());
});
