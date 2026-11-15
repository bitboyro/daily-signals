import assert from 'node:assert/strict';
import test from 'node:test';
import { renderAdminSettings, saveAdminSettings } from '../src/admin-settings.ts';
import type { StationSetup } from '../src/setup.ts';

const setup: StationSetup = {
  schemaVersion: 1, configured: true,
  sources: [{ id: 'desk', name: 'Desk', url: 'https://news.example/rss', priority: 10 }],
  voice: 'fixture', zone: 'UTC', window: '06:00',
};

test('admin settings renders all station concerns inside the producer workspace', () => {
  const html = renderAdminSettings(setup);
  assert.match(html, /Station settings/);
  assert.match(html, /News source/);
  assert.match(html, /Station voice/);
  assert.match(html, /Daily schedule/);
});

test('admin settings refuses an anonymous save before making a request', async () => {
  const form = { elements: { namedItem: () => ({ value: '' }) } } as unknown as HTMLFormElement;
  await assert.rejects(saveAdminSettings('http://api.test', form, ''), /station sign-in is required/);
});
