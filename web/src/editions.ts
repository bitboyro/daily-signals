import { escapeHtml } from './html.ts';

export interface EditionSummary {
  editionId: string;
  airDate: string;
  title: string;
  audioPath: string;
}

function isSummary(value: unknown): value is EditionSummary {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return typeof v.editionId === 'string' && /^[a-z0-9-]+$/.test(v.editionId)
    && typeof v.airDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v.airDate)
    && typeof v.title === 'string' && v.title.trim() !== ''
    && typeof v.audioPath === 'string' && v.audioPath.startsWith('/');
}

export function parseEditionList(payload: unknown): EditionSummary[] {
  if (!Array.isArray(payload) || !payload.every(isSummary)) {
    throw new Error('unexpected edition list response');
  }
  return [...payload].sort((a, b) => b.airDate.localeCompare(a.airDate));
}

export async function fetchEditions(apiBase: string, fetchImpl: typeof fetch = fetch): Promise<EditionSummary[]> {
  const response = await fetchImpl(`${apiBase}/api/editions`, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`edition list unavailable: ${response.status}`);
  return parseEditionList(await response.json());
}

export function renderEditionList(editions: EditionSummary[]): string {
  if (editions.length === 0) return '<p class="empty">No editions have been published yet.</p>';
  const items = editions.map((e) =>
    `<li><a href="#edition-${escapeHtml(e.editionId)}" data-edition="${escapeHtml(e.editionId)}">`
    + `<time datetime="${escapeHtml(e.airDate)}">${escapeHtml(e.airDate)}</time> ${escapeHtml(e.title)}</a></li>`);
  return `<ul class="editions">${items.join('')}</ul>`;
}
