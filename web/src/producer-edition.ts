import { escapeHtml } from './html.ts';

export type EditionStatus = 'DRAFT' | 'READY' | 'PUBLISHED' | 'FAILED';
export interface EditionState { editionId: string; status: EditionStatus; revision: number; }
export interface ApprovalEvent { editionRevision: number; actor: string; approvedAt: string; }
export interface ApprovalAudit { editionId: string; approvals: ApprovalEvent[]; }

function parseState(value: unknown): EditionState {
  const state = value as Record<string, unknown> | null;
  if (!state || state.schemaVersion !== 1 || typeof state.editionId !== 'string'
      || !['DRAFT', 'READY', 'PUBLISHED', 'FAILED'].includes(String(state.status))
      || !Number.isInteger(state.revision) || (state.revision as number) < 0) {
    throw new Error('unexpected edition state');
  }
  return { editionId: state.editionId, status: state.status as EditionStatus, revision: state.revision as number };
}

function parseAudit(value: unknown): ApprovalAudit {
  const audit = value as Record<string, unknown> | null;
  if (!audit || audit.schemaVersion !== 1 || typeof audit.editionId !== 'string' || !Array.isArray(audit.approvals)) {
    throw new Error('unexpected approval audit');
  }
  const approvals = audit.approvals.map((value) => {
    const event = value as Record<string, unknown> | null;
    if (!event || !Number.isInteger(event.editionRevision) || (event.editionRevision as number) < 1
        || typeof event.actor !== 'string' || event.actor.length === 0
        || typeof event.approvedAt !== 'string' || Number.isNaN(Date.parse(event.approvedAt))) {
      throw new Error('unexpected approval event');
    }
    return { editionRevision: event.editionRevision as number, actor: event.actor, approvedAt: event.approvedAt };
  });
  return { editionId: audit.editionId, approvals };
}

export async function fetchApprovalAudit(apiBase: string, token: string, editionId = 'producer-today',
  fetchImpl: typeof fetch = fetch): Promise<ApprovalAudit> {
  const response = await fetchImpl(`${apiBase}/api/producer/editions/${encodeURIComponent(editionId)}/audit`,
    { headers: { Accept: 'application/json', Authorization: `Bearer ${token}` } });
  if (!response.ok) throw new Error(`approval history unavailable: ${response.status}`);
  return parseAudit(await response.json());
}

export function renderApprovalHistory(audit: ApprovalAudit): string {
  const items = audit.approvals.length === 0
    ? '<p>No approval has been recorded for this edition.</p>'
    : '<ol class="approval-history">' + audit.approvals.map((event) => '<li>'
      + `<strong>${escapeHtml(event.actor)}</strong> approved revision ${event.editionRevision} `
      + `<time datetime="${escapeHtml(event.approvedAt)}">${escapeHtml(event.approvedAt)}</time>`
      + '</li>').join('') + '</ol>';
  return '<section aria-labelledby="approval-history-heading">'
    + '<h2 id="approval-history-heading">Approval history</h2>' + items + '</section>';
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
