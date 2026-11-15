import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatDate, formatDateTime, localeTag, message, normalizeLocale, supportedLocales } from '../src/i18n.ts';

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

test('formats stable dates for Romanian and English readers', () => {
  assert.equal(localeTag('en'), 'en-GB');
  assert.equal(localeTag('ro'), 'ro-RO');
  assert.equal(formatDate('2026-01-16', 'en'), '16 January 2026');
  assert.match(formatDate('2026-01-16', 'ro'), /16 ianuarie 2026/);
  assert.match(formatDateTime('2026-11-09T19:40:00Z', 'ro'), /9 nov\. 2026/);
  assert.throws(() => formatDate('today', 'en'), /invalid date/);
});
