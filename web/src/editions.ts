import { escapeHtml } from './html.ts';
import { readEnvelope } from './schema.ts';

export interface EditionSummary {
  editionId: string;
  airDate: string;
  title: string;
  audioPath: string;
}

export interface SchedulePreview { airDate: string; stationTime: string; zone: string; }

export function formatAirDate(airDate: string, locale = 'en-GB'): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(airDate)) throw new Error('invalid air date');
  return new Intl.DateTimeFormat(locale, { dateStyle: 'long', timeZone: 'UTC' }).format(new Date(`${airDate}T00:00:00Z`));
}

export function renderSchedulePreview(preview: SchedulePreview): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(preview.airDate) || !/^\d{2}:\d{2}$/.test(preview.stationTime) || !preview.zone) {
    throw new Error('invalid schedule preview');
  }
  return `<p class="schedule-preview">Next edition: <time datetime="${escapeHtml(preview.airDate)}">${escapeHtml(preview.airDate)}</time> at ${escapeHtml(preview.stationTime)} ${escapeHtml(preview.zone)}</p>`;
}

function isSummary(value: unknown): value is EditionSummary {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return typeof v.editionId === 'string' && /^[a-z0-9-]+$/.test(v.editionId)
    && typeof v.airDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v.airDate)
    && typeof v.title === 'string' && v.title.trim() !== ''
    && typeof v.audioPath === 'string' && v.audioPath.startsWith('/');
}

/** Reads the v2 envelope; a bare v1 array is still accepted from older API deployments. */
export function parseEditionList(payload: unknown): EditionSummary[] {
  const editions = Array.isArray(payload) ? payload : readEnvelope(payload, 'editions');
  if (!editions.every(isSummary)) {
    throw new Error('unexpected edition list response');
  }
  return [...editions].sort((a, b) => b.airDate.localeCompare(a.airDate));
}

export async function fetchEditions(apiBase: string, fetchImpl: typeof fetch = fetch): Promise<EditionSummary[]> {
  const response = await fetchImpl(`${apiBase}/api/editions`, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`edition list unavailable: ${response.status}`);
  return parseEditionList(await response.json());
}

export function renderEditionList(editions: EditionSummary[]): string {
  if (editions.length === 0) return '<p class="empty">No editions have been published yet. Please check back after the next scheduled broadcast.</p>';
  const items = editions.map((e) =>
    `<li><a href="#edition-${escapeHtml(e.editionId)}" data-edition="${escapeHtml(e.editionId)}">`
    + `<time datetime="${escapeHtml(e.airDate)}">${escapeHtml(e.airDate)}</time> ${escapeHtml(e.title)}</a></li>`);
  return `<ul class="editions">${items.join('')}</ul>`;
}

/** Context shown above a selected edition without duplicating the player controls. */
export function renderDailyEdition(edition: EditionSummary): string {
  return `<p class="daily-edition" aria-live="polite">Selected daily edition: <time datetime="${escapeHtml(edition.airDate)}">${escapeHtml(edition.airDate)}</time> — ${escapeHtml(edition.title)}</p>`;
}
