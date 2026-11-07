import { escapeHtml } from './html.ts';

export interface FetchSchedule {
  schemaVersion: 1;
  nextRun: string;
  zone: string;
  window: string;
  sources: string[];
}

export function parseFetchSchedule(payload: unknown): FetchSchedule {
  if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) throw new Error('unexpected fetch schedule response');
  const value = payload as Record<string, unknown>;
  const sources = value.sources;
  if (value.schemaVersion !== 1 || typeof value.nextRun !== 'string' || Number.isNaN(Date.parse(value.nextRun))
    || typeof value.zone !== 'string' || value.zone === '' || typeof value.window !== 'string'
    || !/^\d{2}:\d{2}$/.test(value.window) || !Array.isArray(sources) || sources.length === 0
    || !sources.every((source) => typeof source === 'string' && /^[a-z][a-z0-9-]*$/.test(source))) {
    throw new Error('unexpected fetch schedule response');
  }
  return value as unknown as FetchSchedule;
}

export async function fetchSchedule(apiBase: string, fetchImpl: typeof fetch = fetch): Promise<FetchSchedule> {
  const response = await fetchImpl(`${apiBase}/api/schedule`, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`fetch schedule unavailable: ${response.status}`);
  return parseFetchSchedule(await response.json());
}

export function renderFetchSchedule(schedule: FetchSchedule, locale = 'en-GB'): string {
  const next = new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium', timeStyle: 'short', timeZone: schedule.zone,
  }).format(new Date(schedule.nextRun));
  return '<section class="fetch-schedule" aria-labelledby="fetch-schedule-title">'
    + '<h3 id="fetch-schedule-title">Next source fetch</h3>'
    + `<p><time datetime="${escapeHtml(schedule.nextRun)}">${escapeHtml(next)}</time> `
    + `(${escapeHtml(schedule.zone)}) from ${schedule.sources.length} source${schedule.sources.length === 1 ? '' : 's'}.</p></section>`;
}
