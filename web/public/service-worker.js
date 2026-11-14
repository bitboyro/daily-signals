const CACHE = 'daily-signals-shell-v1';
const EDITION_CACHE = 'daily-signals-edition-v1';
const SHELL = ['/', '/styles.css', '/runtime-config.js', '/app/main.js', '/manifest.webmanifest'];

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
  const urls = [message.manifestUrl, message.audioUrl].map((value) => {
    const url = new URL(value, self.location.origin);
    if (url.origin !== self.location.origin) throw new Error('offline edition must be same-origin');
    return url.href;
  });
  const responses = await Promise.all(urls.map((url) => fetch(url)));
  if (responses.some((response) => !response.ok)) throw new Error('offline edition unavailable');
  const cache = await caches.open(EDITION_CACHE);
  await Promise.all((await cache.keys()).map((request) => cache.delete(request)));
  await Promise.all(urls.map((url, index) => cache.put(url, responses[index])));
}

self.addEventListener('message', (event) => {
  if (event.data?.type === 'CACHE_EDITION') event.waitUntil(cacheEdition(event.data));
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET' || new URL(event.request.url).origin !== self.location.origin) return;
  event.respondWith(fetch(event.request).catch(() => caches.match(event.request)));
});
