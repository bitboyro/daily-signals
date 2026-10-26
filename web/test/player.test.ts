import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readConfig } from '../src/config.ts';
import { audioUrl, nowPlayingState, playbackControls, renderNoEditionGuidance, renderPlayer } from '../src/player.ts';

const edition = { editionId: 'demo-2026-01-15', airDate: '2026-01-15', title: 'Morning & "late" news', audioPath: '/editions/demo/audio' };
const config = readConfig('http://listener.test/', { streamBase: 'http://stream.test/' });

test('player audio comes from the stream base', () => {
  assert.equal(audioUrl(config, edition), 'http://stream.test/editions/demo/audio');
});

test('now-playing state identifies the selected daily edition', () => {
  assert.deepEqual(nowPlayingState(edition), { editionId: 'demo-2026-01-15', title: 'Morning & "late" news', airDate: '2026-01-15' });
});

test('playback controls are explicit and retain the recording download', () => {
  assert.deepEqual(playbackControls(config, edition), { nativeControls: true, preload: 'none', downloadUrl: 'http://stream.test/editions/demo/audio' });
});

test('player is labelled, described and keyboard operable', () => {
  const html = renderPlayer(config, edition);
  assert.match(html, /<audio controls /);
  assert.match(html, /aria-labelledby="player-title-demo-2026-01-15"/);
  assert.match(html, /<figcaption id="player-title-demo-2026-01-15">/);
  assert.match(html, /aria-describedby="player-help-demo-2026-01-15"/);
  assert.match(html, /aria-keyshortcuts="Space Enter ArrowLeft ArrowRight"/);
  assert.match(html, /id="player-help-demo-2026-01-15"/);
});

test('player never autoplays and offers a download fallback', () => {
  const html = renderPlayer(config, edition);
  assert.doesNotMatch(html, /autoplay/);
  assert.match(html, /preload="none"/);
  assert.match(html, /<article class="daily-edition" data-edition="demo-2026-01-15"/);
  assert.match(html, /<a href="http:\/\/stream.test\/editions\/demo\/audio">Download the Morning &amp; &quot;late&quot; news recording<\/a>/);
});

test('empty daily edition guidance gives listeners a useful next step', () => {
  assert.match(renderNoEditionGuidance(), /archive/);
  assert.match(renderNoEditionGuidance(), /next scheduled broadcast/);
});
