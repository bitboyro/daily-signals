import type { ListenerConfig } from './config.ts';
import type { EditionSummary } from './editions.ts';

type ServiceWorkerNavigator = Pick<Navigator, 'serviceWorker'> | Record<string, never>;

export async function registerServiceWorker(
  navigatorImpl: ServiceWorkerNavigator = navigator,
): Promise<ServiceWorkerRegistration | undefined> {
  if (!('serviceWorker' in navigatorImpl)) return undefined;
  return navigatorImpl.serviceWorker.register('/service-worker.js', { scope: '/' });
}

/** Ask the active worker to retain the selected daily edition for an offline replay. */
export async function cacheEditionForOffline(
  config: ListenerConfig,
  edition: EditionSummary,
  navigatorImpl: ServiceWorkerNavigator = navigator,
): Promise<boolean> {
  if (!('serviceWorker' in navigatorImpl)) return false;
  const registration = await navigatorImpl.serviceWorker.ready;
  const worker = navigatorImpl.serviceWorker.controller ?? registration.active;
  if (!worker) return false;
  worker.postMessage({
    type: 'CACHE_EDITION',
    editionId: edition.editionId,
    manifestUrl: `${config.streamBase}/editions/${encodeURIComponent(edition.editionId)}/manifest`,
    audioUrl: `${config.streamBase}${edition.audioPath}`,
  });
  return true;
}
