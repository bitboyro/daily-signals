import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isProducerPanel, renderProducerWorkspace } from '../src/producer-workspace.ts';

test('producer workspace identifies supported panels', () => {
  assert.equal(isProducerPanel('candidates'), true);
  assert.equal(isProducerPanel('script'), true);
  assert.equal(isProducerPanel('pronunciations'), true);
  assert.equal(isProducerPanel('queue'), true);
  assert.equal(isProducerPanel('history'), true);
  assert.equal(isProducerPanel('settings'), true);
  assert.equal(isProducerPanel('publish'), false);
});

test('producer workspace exposes an accessible active panel', () => {
  const html = renderProducerWorkspace('settings');
  assert.match(html, /role="tablist" aria-label="Producer workspace"/);
  assert.match(html, /data-producer-panel="settings" aria-selected="true"/);
  assert.match(html, /data-producer-panel="queue" aria-selected="false" aria-controls="producer-panel-queue" tabindex="-1"/);
  assert.match(html, /Today’s candidates/);
  assert.match(html, /Pronunciations/);
  assert.match(html, /Approval history/);
  assert.match(html, /Admin settings/);
  assert.match(html, /id="producer-panel-settings" role="tabpanel" aria-labelledby="producer-tab-settings"/);
});

test('producer tabs expose reciprocal keyboard navigation metadata', () => {
  const html = renderProducerWorkspace('queue');
  assert.match(html, /role="tab" id="producer-tab-queue"/);
  assert.match(html, /aria-controls="producer-panel-queue"/);
  assert.match(html, /role="tabpanel" aria-labelledby="producer-tab-queue"/);
});
