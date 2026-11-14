type ServiceWorkerNavigator = Pick<Navigator, 'serviceWorker'> | Record<string, never>;

export async function registerServiceWorker(
  navigatorImpl: ServiceWorkerNavigator = navigator,
): Promise<ServiceWorkerRegistration | undefined> {
  if (!('serviceWorker' in navigatorImpl)) return undefined;
  return navigatorImpl.serviceWorker.register('/service-worker.js', { scope: '/' });
}
