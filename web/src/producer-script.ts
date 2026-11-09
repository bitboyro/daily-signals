import { escapeHtml } from './html.ts';

export interface ScriptDraft { text: string; revision: number; }

function parseDraft(value: unknown): ScriptDraft {
  const draft = value as Record<string, unknown> | null;
  if (!draft || draft.schemaVersion !== 1 || typeof draft.text !== 'string' || draft.text.length === 0
      || draft.text.length > 20_000 || !Number.isInteger(draft.revision) || (draft.revision as number) < 0) {
    throw new Error('unexpected script response');
  }
  return { text: draft.text, revision: draft.revision as number };
}

export async function fetchScript(apiBase: string, token: string, fetchImpl: typeof fetch = fetch): Promise<ScriptDraft> {
  const response = await fetchImpl(`${apiBase}/api/producer/script`, { headers: { Accept: 'application/json', Authorization: `Bearer ${token}` } });
  if (response.status === 401) throw new Error('sign in before editing the script');
  if (response.status === 403) throw new Error('your role cannot edit the script');
  if (!response.ok) throw new Error(`script unavailable: ${response.status}`);
  return parseDraft(await response.json());
}

export async function saveScript(apiBase: string, token: string, text: string,
  fetchImpl: typeof fetch = fetch): Promise<ScriptDraft> {
  const response = await fetchImpl(`${apiBase}/api/producer/script`, { method: 'PUT', body: text,
    headers: { Accept: 'application/json', 'Content-Type': 'text/plain', Authorization: `Bearer ${token}` } });
  if (response.status === 401) throw new Error('sign in before saving the script');
  if (response.status === 403) throw new Error('your role cannot edit the script');
  if (response.status === 400) throw new Error('script must contain 1 to 20000 printable characters');
  if (!response.ok) throw new Error(`script save failed: ${response.status}`);
  return parseDraft(await response.json());
}

export async function generateScriptAudio(apiBase: string, token: string,
  fetchImpl: typeof fetch = fetch): Promise<Blob> {
  const response = await fetchImpl(`${apiBase}/api/producer/audio`, { method: 'POST',
    headers: { Accept: 'audio/wav', Authorization: `Bearer ${token}` } });
  if (response.status === 401) throw new Error('sign in before generating audio');
  if (response.status === 403) throw new Error('your role cannot generate audio');
  if (!response.ok) throw new Error(`audio generation failed: ${response.status}`);
  const audio = await response.blob();
  if (audio.type !== 'audio/wav' || audio.size < 4) throw new Error('voice provider returned invalid audio');
  return audio;
}

export function renderScriptEditor(draft: ScriptDraft): string {
  return '<form class="producer-script">'
    + `<label for="edition-script">Edition script</label><textarea id="edition-script" name="script" maxlength="20000" rows="14">${escapeHtml(draft.text)}</textarea>`
    + `<p>Revision ${draft.revision}</p><button type="submit">Save script</button>`
    + '<button type="button" data-generate-audio>Generate audio preview</button>'
    + '<audio controls hidden data-audio-preview></audio><p class="script-status" role="status"></p></form>';
}
