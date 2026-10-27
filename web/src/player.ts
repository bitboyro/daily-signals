import type { ListenerConfig } from './config.ts';
import type { EditionSummary } from './editions.ts';
import { escapeHtml } from './html.ts';

export function audioUrl(config: ListenerConfig, edition: EditionSummary): string {
  return `${config.streamBase}${edition.audioPath}`;
}

export interface NowPlayingState { editionId: string; title: string; airDate: string; }
export interface PlaybackControls { preload: 'none'; nativeControls: true; downloadUrl: string; }
export type RecoveryState = 'idle' | 'retryable' | 'exhausted';

export function recoveryState(failures: number, maxRetries = 1): RecoveryState {
  if (failures === 0) return 'idle';
  return failures <= maxRetries ? 'retryable' : 'exhausted';
}

export function nowPlayingState(edition: EditionSummary): NowPlayingState {
  return { editionId: edition.editionId, title: edition.title, airDate: edition.airDate };
}

export function playbackControls(config: ListenerConfig, edition: EditionSummary): PlaybackControls {
  return { preload: 'none', nativeControls: true, downloadUrl: audioUrl(config, edition) };
}

export function renderNoEditionGuidance(): string {
  return '<p class="empty listener-empty">Today\'s edition is being prepared. Check the archive or return after the next scheduled broadcast.</p>';
}

/** Native controls keep keyboard and screen-reader support; nothing plays until the listener asks. */
export function renderPlayer(config: ListenerConfig, edition: EditionSummary): string {
  const id = escapeHtml(edition.editionId);
  const title = escapeHtml(edition.title);
  const src = escapeHtml(audioUrl(config, edition));
  const controls = playbackControls(config, edition);
  return `<article class="daily-edition" data-edition="${id}" aria-labelledby="player-title-${id}"><figure class="player" aria-labelledby="player-title-${id}">`
    + `<figcaption id="player-title-${id}">${title} <time datetime="${escapeHtml(edition.airDate)}">${escapeHtml(edition.airDate)}</time></figcaption>`
    + `<audio controls preload="${controls.preload}" src="${src}" aria-labelledby="player-title-${id}" aria-describedby="player-help-${id}" aria-keyshortcuts="Space Enter ArrowLeft ArrowRight">`
    + `<a href="${src}">Download the ${title} recording</a></audio>`
    + `<p id="player-help-${id}" class="player-help">Space or Enter plays and pauses; arrow keys seek. Playback does not start automatically.</p>`
    + `<p class="playback-status" role="status" aria-live="polite">Ready to play.</p>`
    + `<button class="playback-retry" type="button" hidden>Retry playback</button>`
    + `<p class="playback-download">If streaming remains unavailable, <a href="${src}">download the recording</a>.</p>`
    + '</figure></article>';
}

export function bindPlaybackRecovery(root: ParentNode, maxRetries = 1): void {
  const audio = root.querySelector<HTMLAudioElement>('audio');
  const retry = root.querySelector<HTMLButtonElement>('.playback-retry');
  const status = root.querySelector<HTMLElement>('.playback-status');
  if (!audio || !retry || !status) return;
  let failures = 0;
  audio.addEventListener('error', () => {
    failures += 1;
    const state = recoveryState(failures, maxRetries);
    retry.hidden = state !== 'retryable';
    status.textContent = state === 'retryable'
      ? 'Playback failed. You can retry once or download the recording.'
      : 'Playback is unavailable. Download the recording or try again later.';
  });
  retry.addEventListener('click', () => {
    retry.hidden = true;
    status.textContent = 'Retrying playback…';
    audio.load();
    void audio.play().catch(() => {
      audio.dispatchEvent(new Event('error'));
    });
  });
}
