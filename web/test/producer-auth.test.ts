import { test } from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { beginProducerSignIn, completeProducerSignIn, producerBearer, signOutProducer } from '../src/producer-auth.ts';

class MemoryStorage implements Storage {
  #values = new Map<string, string>();
  get length() { return this.#values.size; }
  clear() { this.#values.clear(); }
  getItem(key: string) { return this.#values.get(key) ?? null; }
  key(index: number) { return [...this.#values.keys()][index] ?? null; }
  removeItem(key: string) { this.#values.delete(key); }
  setItem(key: string, value: string) { this.#values.set(key, value); }
}

const config = { issuer: 'https://id.example/realms/station', clientId: 'daily-signals-web', redirectUri: 'https://radio.example/' };
const discovery = { authorization_endpoint: 'https://id.example/authorize', token_endpoint: 'https://id.example/token' };

test('starts authorization code with S256 PKCE and opaque state', async () => {
  const storage = new MemoryStorage();
  const fetchImpl = async () => new Response(JSON.stringify(discovery));
  const target = new URL(await beginProducerSignIn(config, storage, webcrypto as Crypto, fetchImpl as typeof fetch));
  assert.equal(target.origin + target.pathname, discovery.authorization_endpoint);
  assert.equal(target.searchParams.get('response_type'), 'code');
  assert.equal(target.searchParams.get('code_challenge_method'), 'S256');
  assert.ok(target.searchParams.get('code_challenge'));
  assert.ok(target.searchParams.get('state'));
});

test('verifies callback state, exchanges the code and prefers the session token', async () => {
  const storage = new MemoryStorage();
  const fetchImpl = async (input: string | URL | Request) => String(input).endsWith('/token')
    ? new Response(JSON.stringify({ access_token: 'oidc-access' })) : new Response(JSON.stringify(discovery));
  const signIn = new URL(await beginProducerSignIn(config, storage, webcrypto as Crypto, fetchImpl as typeof fetch));
  const callback = `${config.redirectUri}?code=code-1&state=${signIn.searchParams.get('state')}`;
  assert.equal(await completeProducerSignIn(callback, config, storage, fetchImpl as typeof fetch), 'oidc-access');
  assert.equal(producerBearer(storage, 'local-token'), 'oidc-access');
  signOutProducer(storage);
  assert.equal(producerBearer(storage, ' local-token '), 'local-token');
});

test('rejects a callback whose state was not initiated in this browser session', async () => {
  await assert.rejects(completeProducerSignIn(`${config.redirectUri}?code=x&state=wrong`, config,
    new MemoryStorage()), /could not be verified/);
});
