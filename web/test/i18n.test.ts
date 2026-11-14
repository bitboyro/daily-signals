import { test } from 'node:test';
import assert from 'node:assert/strict';
import { message, normalizeLocale, supportedLocales } from '../src/i18n.ts';

test('normalizes Romanian variants and conservatively falls back to English', () => {
  assert.equal(normalizeLocale('ro-RO'), 'ro');
  assert.equal(normalizeLocale('RO_ro'), 'ro');
  assert.equal(normalizeLocale('en-GB'), 'en');
  assert.equal(normalizeLocale('fr-FR'), 'en');
  assert.deepEqual(supportedLocales, ['en', 'ro']);
});

test('generated bundles expose matching Romanian and English messages', () => {
  assert.equal(message('en', 'listener.today'), 'Today’s edition');
  assert.equal(message('ro', 'listener.today'), 'Ediția de astăzi');
  assert.equal(message('ro', 'date.nextEdition'), 'Următoarea ediție');
});
