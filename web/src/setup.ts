import { escapeHtml } from './html.ts';
import { validateSources, type SourceConfig } from './source-editor.ts';

export type StationSetup = {
  schemaVersion: 1;
  configured: boolean;
  sources: SourceConfig[];
  voice: 'fixture' | 'kokoro';
  zone: string;
  window: string;
};

export function parseSetup(payload: unknown): StationSetup {
  if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) throw new Error('unexpected setup response');
  const value = payload as Partial<StationSetup>;
  if (value.schemaVersion !== 1 || typeof value.configured !== 'boolean' || !Array.isArray(value.sources)
    || validateSources(value.sources as SourceConfig[]).length > 0
    || !['fixture', 'kokoro'].includes(value.voice ?? '')
    || typeof value.zone !== 'string' || value.zone.length === 0
    || typeof value.window !== 'string' || !/^\d{2}:\d{2}$/.test(value.window)) {
    throw new Error('unexpected setup response');
  }
  return value as StationSetup;
}

export async function fetchSetup(apiBase: string, fetchImpl: typeof fetch = fetch): Promise<StationSetup> {
  const response = await fetchImpl(`${apiBase}/api/setup`, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`setup unavailable: ${response.status}`);
  return parseSetup(await response.json());
}

export async function saveSetup(apiBase: string, setup: StationSetup, bearer = '',
  fetchImpl: typeof fetch = fetch): Promise<StationSetup> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'application/json' };
  if (bearer) headers.Authorization = `Bearer ${bearer}`;
  const response = await fetchImpl(`${apiBase}/api/setup`, { method: 'PUT', headers, body: JSON.stringify(setup) });
  if (!response.ok) throw new Error(`setup save failed: ${response.status}`);
  return parseSetup(await response.json());
}

export function renderSetup(setup: StationSetup): string {
  const source = setup.sources[0];
  return `<form class="station-setup" novalidate><p>${setup.configured ? 'Station setup is complete. You can review it here.' : 'Three short steps prepare your station.'}</p>`
    + '<fieldset><legend>1. News source</legend>'
    + `<label for="setup-source-name">Name</label><input id="setup-source-name" name="sourceName" value="${escapeHtml(source.name)}" required>`
    + `<label for="setup-source-id">Identifier</label><input id="setup-source-id" name="sourceId" value="${escapeHtml(source.id)}" pattern="[a-z][a-z0-9-]*" required>`
    + `<label for="setup-source-url">HTTPS feed URL</label><input id="setup-source-url" name="sourceUrl" type="url" value="${escapeHtml(source.url)}" required></fieldset>`
    + '<fieldset><legend>2. Station voice</legend><label for="setup-voice">Voice provider</label>'
    + `<select id="setup-voice" name="voice"><option value="fixture"${setup.voice === 'fixture' ? ' selected' : ''}>Built-in test voice</option>`
    + `<option value="kokoro"${setup.voice === 'kokoro' ? ' selected' : ''}>Local Kokoro (requires the kokoro install profile)</option></select>`
    + '<p class="field-help">To use Kokoro, start Daily Signals with <code>docker compose --profile kokoro up --build</code>.</p></fieldset>'
    + '<fieldset><legend>3. Daily schedule</legend>'
    + `<label for="setup-window">Fetch time</label><input id="setup-window" name="window" type="time" value="${escapeHtml(setup.window)}" required>`
    + `<label for="setup-zone">Timezone</label><input id="setup-zone" name="zone" value="${escapeHtml(setup.zone)}" required></fieldset>`
    + '<button type="submit">Save station setup</button><p class="setup-status" role="status" aria-live="polite"></p></form>';
}

export function setupFromForm(form: HTMLFormElement): StationSetup {
  const value = (name: string) => (form.elements.namedItem(name) as HTMLInputElement | HTMLSelectElement).value;
  const setup: StationSetup = {
    schemaVersion: 1, configured: true,
    sources: [{ id: value('sourceId'), name: value('sourceName'), url: value('sourceUrl'), priority: 10 }],
    voice: value('voice') as StationSetup['voice'], zone: value('zone'), window: value('window'),
  };
  const problems = validateSources(setup.sources);
  try { new Intl.DateTimeFormat('en', { timeZone: setup.zone }); } catch { problems.push('timezone is invalid'); }
  if (!/^\d{2}:\d{2}$/.test(setup.window)) problems.push('fetch time is invalid');
  if (problems.length) throw new Error(problems.join('. '));
  return setup;
}
