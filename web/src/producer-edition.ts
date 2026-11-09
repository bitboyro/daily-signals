export type EditionStatus = 'DRAFT' | 'READY' | 'PUBLISHED';
export interface EditionState { editionId: string; status: EditionStatus; revision: number; }

function parseState(value: unknown): EditionState {
  const state = value as Record<string, unknown> | null;
  if (!state || state.schemaVersion !== 1 || typeof state.editionId !== 'string'
      || !['DRAFT', 'READY', 'PUBLISHED'].includes(String(state.status))
      || !Number.isInteger(state.revision) || (state.revision as number) < 0) {
    throw new Error('unexpected edition state');
  }
  return { editionId: state.editionId, status: state.status as EditionStatus, revision: state.revision as number };
}

export async function fetchEditionState(apiBase: string, token: string,
  fetchImpl: typeof fetch = fetch): Promise<EditionState> {
  const response = await fetchImpl(`${apiBase}/api/producer/edition`,
    { headers: { Accept: 'application/json', Authorization: `Bearer ${token}` } });
  if (!response.ok) throw new Error(`edition state unavailable: ${response.status}`);
  return parseState(await response.json());
}

export async function transitionEdition(apiBase: string, token: string, action: 'APPROVE' | 'PUBLISH',
  fetchImpl: typeof fetch = fetch): Promise<EditionState> {
  const response = await fetchImpl(`${apiBase}/api/producer/edition`, { method: 'POST', body: action,
    headers: { Accept: 'application/json', 'Content-Type': 'text/plain', Authorization: `Bearer ${token}` } });
  if (response.status === 409) throw new Error('edition is not ready for that action');
  if (!response.ok) throw new Error(`edition action failed: ${response.status}`);
  return parseState(await response.json());
}
