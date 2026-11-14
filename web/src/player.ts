import type { ListenerConfig } from './config.ts';
import type { EditionSummary } from './editions.ts';
import { escapeHtml } from './html.ts';

export function audioUrl(config: ListenerConfig, edition: EditionSummary): string {
  return `${config.streamBase}${edition.audioPath}`;
}

export interface NowPlayingState { editionId: string; title: string; airDate: string; }
export interface PlaybackControls { preload: 'none'; nativeControls: true; downloadUrl: string; }
export type RecoveryState = 'idle' | 'retryable' | 'exhausted';
export type PlaybackKeyAction = 'toggle' | 'seek-back' | 'seek-forward' | 'previous-chapter' | 'next-chapter';

const KEY_ACTIONS: Readonly<Record<string, PlaybackKeyAction>> = {
  ' ': 'toggle',
  Enter: 'toggle',
  ArrowLeft: 'seek-back',
  ArrowRight: 'seek-forward',
  p: 'previous-chapter',
  P: 'previous-chapter',
  n: 'next-chapter',
  N: 'next-chapter',
};

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

export function playbackKeyAction(key: string): PlaybackKeyAction | undefined {
  return KEY_ACTIONS[key];
}

export function chapterTarget(offsets: number[], currentSeconds: number,
                              direction: 'previous' | 'next'): number | undefined {
  const ordered = offsets.filter(Number.isFinite).sort((left, right) => left - right);
  if (direction === 'next') return ordered.find((offset) => offset > currentSeconds + 0.25);
  for (let index = ordered.length - 1; index >= 0; index -= 1) {
    if (ordered[index] < currentSeconds - 0.25) return ordered[index];
  }
  return undefined;
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
    + `<audio controls preload="${controls.preload}" src="${src}" aria-labelledby="player-title-${id}" aria-describedby="player-help-${id}" aria-keyshortcuts="Space Enter ArrowLeft ArrowRight N P">`
    + `<a href="${src}">Download the ${title} recording</a></audio>`
    + `<p id="player-help-${id}" class="player-help">Space or Enter plays and pauses; arrow keys seek 10 seconds; N and P move between chapters. Playback does not start automatically.</p>`
    + `<p class="playback-status" role="status" aria-live="polite" aria-atomic="true">Ready to play ${title}.</p>`
    + `<button class="playback-retry" type="button" aria-label="Retry ${title}" hidden>Retry playback</button>`
    + `<p class="playback-download">If streaming remains unavailable, <a href="${src}">download the recording</a>.</p>`
    + '</figure></article>';
}

export function bindPlaybackAnnouncements(root: HTMLElement, title: string): void {
  const audio = root.querySelector<HTMLAudioElement>('audio');
  const status = root.querySelector<HTMLElement>('.playback-status');
  if (!audio || !status) return;
  audio.addEventListener('play', () => { status.textContent = `Playing ${title}.`; });
  audio.addEventListener('pause', () => {
    if (!audio.ended) status.textContent = `Paused ${title} at ${Math.floor(audio.currentTime)} seconds.`;
  });
  audio.addEventListener('ended', () => { status.textContent = `${title} finished.`; });
}

export function bindPlaybackKeyboard(root: HTMLElement): void {
  const audio = root.querySelector<HTMLAudioElement>('audio');
  if (!audio) return;
  audio.addEventListener('keydown', (event) => {
    if (event.altKey || event.ctrlKey || event.metaKey) return;
    const action = playbackKeyAction(event.key);
    if (!action) return;
    event.preventDefault();
    if (action === 'toggle') {
      if (audio.paused) void audio.play();
      else audio.pause();
      return;
    }
    if (action === 'seek-back' || action === 'seek-forward') {
      const delta = action === 'seek-back' ? -10 : 10;
      const upperBound = Number.isFinite(audio.duration) ? audio.duration : Number.POSITIVE_INFINITY;
      audio.currentTime = Math.min(upperBound, Math.max(0, audio.currentTime + delta));
      return;
    }
    const offsets = Array.from(root.querySelectorAll<HTMLButtonElement>('button[data-seek-millis]'))
      .map((button) => Number(button.dataset.seekMillis) / 1000);
    const target = chapterTarget(offsets, audio.currentTime, action === 'next-chapter' ? 'next' : 'previous');
    if (target !== undefined) audio.currentTime = target;
  });
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
