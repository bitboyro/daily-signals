import type { ListenerConfig } from './config.ts';
import type { EditionSummary } from './editions.ts';
import { escapeHtml } from './html.ts';

export function audioUrl(config: ListenerConfig, edition: EditionSummary): string {
  return `${config.streamBase}${edition.audioPath}`;
}

/** Native controls keep keyboard and screen-reader support; nothing plays until the listener asks. */
export function renderPlayer(config: ListenerConfig, edition: EditionSummary): string {
  const id = escapeHtml(edition.editionId);
  const title = escapeHtml(edition.title);
  const src = escapeHtml(audioUrl(config, edition));
  return `<figure class="player" aria-labelledby="player-title-${id}">`
    + `<figcaption id="player-title-${id}">${title} <time datetime="${escapeHtml(edition.airDate)}">${escapeHtml(edition.airDate)}</time></figcaption>`
    + `<audio controls preload="none" src="${src}" aria-labelledby="player-title-${id}" aria-describedby="player-help-${id}">`
    + `<a href="${src}">Download the ${title} recording</a></audio>`
    + `<p id="player-help-${id}" class="player-help">Space or Enter plays and pauses; arrow keys seek.</p>`
    + '</figure>';
}
