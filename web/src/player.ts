import type { ListenerConfig } from './config.ts';
import type { EditionSummary } from './editions.ts';
import { escapeHtml } from './html.ts';

export function audioUrl(config: ListenerConfig, edition: EditionSummary): string {
  return `${config.streamBase}${edition.audioPath}`;
}

export interface NowPlayingState { editionId: string; title: string; airDate: string; }
export interface PlaybackControls { preload: 'none'; nativeControls: true; downloadUrl: string; }

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
    + '</figure></article>';
}
