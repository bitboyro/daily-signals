import { escapeHtml } from './html.ts';

export interface PronunciationDraft { text: string; revision: number; }

function parseDraft(value: unknown): PronunciationDraft {
  const draft = value as Record<string, unknown> | null;
  if (!draft || draft.schemaVersion !== 1 || typeof draft.text !== 'string'
      || !Number.isInteger(draft.revision) || (draft.revision as number) < 0) {
    throw new Error('unexpected pronunciation response');
  }
  return { text: draft.text, revision: draft.revision as number };
}

export async function fetchPronunciations(apiBase: string, token: string,
  fetchImpl: typeof fetch = fetch): Promise<PronunciationDraft> {
  const response = await fetchImpl(`${apiBase}/api/producer/pronunciations`,
    { headers: { Accept: 'application/json', Authorization: `Bearer ${token}` } });
  if (response.status === 401) throw new Error('sign in before editing pronunciations');
  if (response.status === 403) throw new Error('your role cannot edit pronunciations');
  if (!response.ok) throw new Error(`pronunciations unavailable: ${response.status}`);
  return parseDraft(await response.json());
}

export async function savePronunciations(apiBase: string, token: string, text: string,
  fetchImpl: typeof fetch = fetch): Promise<PronunciationDraft> {
  const response = await fetchImpl(`${apiBase}/api/producer/pronunciations`, { method: 'PUT', body: text,
    headers: { Accept: 'application/json', 'Content-Type': 'text/plain', Authorization: `Bearer ${token}` } });
  if (response.status === 400) throw new Error('use one unique term=spoken form per line');
  if (!response.ok) throw new Error(`pronunciation save failed: ${response.status}`);
  return parseDraft(await response.json());
}

export function renderPronunciationEditor(draft: PronunciationDraft): string {
  return '<form class="producer-pronunciations">'
    + '<label for="pronunciation-entries">Pronunciation fixes</label>'
    + `<textarea id="pronunciation-entries" name="entries" maxlength="20000" rows="10">${escapeHtml(draft.text)}</textarea>`
    + '<p>Use one <code>term=spoken form</code> per line. Terms match whole words only.</p>'
    + `<p>Revision ${draft.revision}</p><button type="submit">Save pronunciations</button>`
    + '<p class="pronunciation-status" role="status"></p></form>';
}
