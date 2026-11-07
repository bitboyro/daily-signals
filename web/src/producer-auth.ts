export interface ProducerOidcConfig {
  issuer: string;
  clientId: string;
  redirectUri: string;
}

interface OidcDiscovery { authorization_endpoint: string; token_endpoint: string; }

const VERIFIER = 'daily-signals.oidc.verifier';
const STATE = 'daily-signals.oidc.state';
const ACCESS_TOKEN = 'daily-signals.oidc.access-token';

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '');
}

async function discover(config: ProducerOidcConfig, fetchImpl: typeof fetch): Promise<OidcDiscovery> {
  const endpoint = `${config.issuer.replace(/\/$/, '')}/.well-known/openid-configuration`;
  const response = await fetchImpl(endpoint, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error('producer identity provider is unavailable');
  const value = await response.json() as Partial<OidcDiscovery>;
  if (!value.authorization_endpoint || !value.token_endpoint) throw new Error('producer identity provider is incomplete');
  return value as OidcDiscovery;
}

/** Starts authorization-code + PKCE without putting a credential in persistent browser storage. */
export async function beginProducerSignIn(config: ProducerOidcConfig, storage: Storage,
  cryptoImpl: Crypto = crypto, fetchImpl: typeof fetch = fetch): Promise<string> {
  const verifier = base64Url(cryptoImpl.getRandomValues(new Uint8Array(48)));
  const state = base64Url(cryptoImpl.getRandomValues(new Uint8Array(24)));
  const digest = await cryptoImpl.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  storage.setItem(VERIFIER, verifier);
  storage.setItem(STATE, state);
  const metadata = await discover(config, fetchImpl);
  const target = new URL(metadata.authorization_endpoint);
  target.search = new URLSearchParams({ response_type: 'code', client_id: config.clientId,
    redirect_uri: config.redirectUri, scope: 'openid profile', state,
    code_challenge: base64Url(new Uint8Array(digest)), code_challenge_method: 'S256' }).toString();
  return target.toString();
}

/** Completes the callback when present; otherwise returns the current session token. */
export async function completeProducerSignIn(callbackUrl: string, config: ProducerOidcConfig,
  storage: Storage, fetchImpl: typeof fetch = fetch): Promise<string | null> {
  const callback = new URL(callbackUrl);
  const code = callback.searchParams.get('code');
  if (!code) return storage.getItem(ACCESS_TOKEN);
  const expectedState = storage.getItem(STATE);
  const verifier = storage.getItem(VERIFIER);
  if (!expectedState || callback.searchParams.get('state') !== expectedState || !verifier) {
    throw new Error('producer sign-in callback could not be verified');
  }
  const metadata = await discover(config, fetchImpl);
  const body = new URLSearchParams({ grant_type: 'authorization_code', client_id: config.clientId,
    redirect_uri: config.redirectUri, code, code_verifier: verifier });
  const response = await fetchImpl(metadata.token_endpoint, { method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body });
  if (!response.ok) throw new Error('producer sign-in could not be completed');
  const result = await response.json() as { access_token?: unknown };
  if (typeof result.access_token !== 'string' || result.access_token.length === 0) {
    throw new Error('producer identity provider returned no access token');
  }
  storage.setItem(ACCESS_TOKEN, result.access_token);
  storage.removeItem(VERIFIER);
  storage.removeItem(STATE);
  return result.access_token;
}

export function producerBearer(storage: Storage, localFallback: string): string {
  return storage.getItem(ACCESS_TOKEN) ?? localFallback.trim();
}

export function signOutProducer(storage: Storage): void {
  storage.removeItem(ACCESS_TOKEN);
  storage.removeItem(VERIFIER);
  storage.removeItem(STATE);
}
