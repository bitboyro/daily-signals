export type EditionStatus = 'DRAFT' | 'READY' | 'PUBLISHED' | 'FAILED';
export interface EditionState { editionId: string; status: EditionStatus; revision: number; }

function parseState(value: unknown): EditionState {
  const state = value as Record<string, unknown> | null;
  if (!state || state.schemaVersion !== 1 || typeof state.editionId !== 'string'
      || !['DRAFT', 'READY', 'PUBLISHED', 'FAILED'].includes(String(state.status))
      || !Number.isInteger(state.revision) || (state.revision as number) < 0) {
    throw new Error('unexpected edition state');
  }
  return { editionId: state.editionId, status: state.status as EditionStatus, revision: state.revision as number };
}

export async function pollEditionState(apiBase: string, token: string, maximumAttempts = 10,
  delayMillis = 250, fetchImpl: typeof fetch = fetch,
  sleep: (milliseconds: number) => Promise<void> = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))): Promise<EditionState> {
  if (!Number.isInteger(maximumAttempts) || maximumAttempts < 1 || delayMillis < 0) throw new Error('invalid polling policy');
  for (let attempt = 1; attempt <= maximumAttempts; attempt += 1) {
    const state = await fetchEditionState(apiBase, token, fetchImpl);
    if (state.status === 'PUBLISHED' || state.status === 'FAILED') return state;
    if (attempt < maximumAttempts) await sleep(delayMillis);
  }
  throw new Error('edition publication did not finish in time');
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
