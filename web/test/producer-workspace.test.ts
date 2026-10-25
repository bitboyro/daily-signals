import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isProducerPanel, renderProducerWorkspace } from '../src/producer-workspace.ts';

test('producer workspace identifies supported panels', () => {
  assert.equal(isProducerPanel('queue'), true);
  assert.equal(isProducerPanel('sources'), true);
  assert.equal(isProducerPanel('publish'), false);
});

test('producer workspace exposes an accessible active panel', () => {
  const html = renderProducerWorkspace('sources');
  assert.match(html, /role="tablist" aria-label="Producer workspace"/);
  assert.match(html, /data-producer-panel="sources" aria-selected="true"/);
  assert.match(html, /id="producer-panel-sources" role="tabpanel" aria-labelledby="producer-tab-sources"/);
});
