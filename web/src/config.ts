import { normalizeLocale, type Locale } from './i18n.ts';

/** Where the listener finds the Java API and the Rust streamer. */
export interface ListenerConfig {
  apiBase: string;
  streamBase: string;
  locale: Locale;
  producerOidc?: { issuer: string; clientId: string; redirectUri: string };
}

export interface ListenerConfigOverrides extends Omit<Partial<ListenerConfig>, 'producerOidc' | 'locale'> {
  locale?: string;
  producerOidc?: { issuer: string; clientId: string; redirectUri?: string };
}

function trimBase(value: string): string {
  return value.endsWith('/') ? value.slice(0, -1) : value;
}

export function readConfig(origin: string, overrides: ListenerConfigOverrides = {}): ListenerConfig {
  const apiBase = trimBase(overrides.apiBase ?? origin);
  const producerOidc = overrides.producerOidc && overrides.producerOidc.issuer && overrides.producerOidc.clientId
    ? { ...overrides.producerOidc, issuer: trimBase(overrides.producerOidc.issuer),
        redirectUri: overrides.producerOidc.redirectUri ?? `${trimBase(origin)}/` }
    : undefined;
  return { apiBase, streamBase: trimBase(overrides.streamBase ?? apiBase), locale: normalizeLocale(overrides.locale), producerOidc };
}
