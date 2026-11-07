import { escapeHtml } from './html.ts';
import { readEnvelope } from './schema.ts';

export type Decision = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface QueueItem {
  storyId: string;
  headline: string;
  source: string;
  decision: Decision;
  review: {
    reviewer: string | null;
    reviewedAt: string | null;
    revision: number;
  };
}

export interface DecisionReceipt {
  storyId: string;
  decision: Exclude<Decision, 'PENDING'>;
}

export type QueueFilter = 'ALL' | Decision;

const DECISIONS: readonly string[] = ['PENDING', 'APPROVED', 'REJECTED'];

function isQueueItem(value: unknown): value is QueueItem {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  const review = v.review as Record<string, unknown> | null;
  return typeof v.storyId === 'string' && typeof v.headline === 'string'
    && typeof v.source === 'string' && typeof v.decision === 'string' && DECISIONS.includes(v.decision)
    && typeof review === 'object' && review !== null
    && (review.reviewer === null || typeof review.reviewer === 'string')
    && (review.reviewedAt === null || (typeof review.reviewedAt === 'string' && !Number.isNaN(Date.parse(review.reviewedAt))))
    && Number.isInteger(review.revision) && (review.revision as number) >= 0;
}

export function parseQueue(payload: unknown): QueueItem[] {
  const items = readEnvelope(payload, 'items', 3);
  if (!items.every(isQueueItem)) throw new Error('unexpected review queue response');
  return items;
}

export function pendingCount(items: QueueItem[]): number {
  return items.filter((item) => item.decision === 'PENDING').length;
}

/** Applies a local producer view filter; decisions and their server-side authority remain unchanged. */
export function filterQueue(items: QueueItem[], filter: QueueFilter, query = ''): QueueItem[] {
  const normalizedQuery = query.trim().toLocaleLowerCase();
  return items.filter((item) => (filter === 'ALL' || item.decision === filter)
    && (!normalizedQuery || `${item.headline} ${item.source}`.toLocaleLowerCase().includes(normalizedQuery)));
}

export async function fetchQueue(apiBase: string, fetchImpl: typeof fetch = fetch): Promise<QueueItem[]> {
  const response = await fetchImpl(`${apiBase}/api/review-queue`, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`review queue unavailable: ${response.status}`);
  return parseQueue(await response.json());
}

/** The Java service maps the reviewer token to a role; the client never asserts one. */
export async function submitDecision(apiBase: string, storyId: string, decision: Exclude<Decision, 'PENDING'>,
  token: string, fetchImpl: typeof fetch = fetch): Promise<DecisionReceipt> {
  const response = await fetchImpl(`${apiBase}/api/review-queue/${encodeURIComponent(storyId)}`, {
    method: 'POST', body: decision, headers: { 'Content-Type': 'text/plain', Authorization: `Bearer ${token}` },
  });
  if (response.status === 401) throw new Error('sign in with a reviewer token first');
  if (response.status === 403) throw new Error('your role cannot review stories');
  if (!response.ok) throw new Error(`decision rejected: ${response.status}`);
  const receipt = await response.json() as Partial<DecisionReceipt>;
  if (receipt.storyId !== storyId || receipt.decision !== decision) throw new Error('unexpected decision response');
  return receipt as DecisionReceipt;
}

function controls(item: QueueItem): string {
  if (item.decision !== 'PENDING') return '';
  const headline = escapeHtml(item.headline);
  const id = escapeHtml(item.storyId);
  return `<button type="button" data-action="APPROVED" data-story="${id}" aria-label="Approve ${headline}">Approve</button>`
    + `<button type="button" data-action="REJECTED" data-story="${id}" aria-label="Reject ${headline}">Reject</button>`;
}

function reviewDetails(item: QueueItem): string {
  if (item.review.reviewer === null || item.review.reviewedAt === null) return 'Not reviewed';
  const reviewedAt = escapeHtml(item.review.reviewedAt);
  return `${escapeHtml(item.review.reviewer)} · <time datetime="${reviewedAt}">${reviewedAt}</time> · r${item.review.revision}`;
}

export function renderQueue(items: QueueItem[], filter: QueueFilter = 'ALL', query = ''): string {
  const visible = filterQueue(items, filter, query);
  const filterControl = '<form class="queue-filter" aria-label="Filter review queue">'
    + '<label for="queue-filter">Decision</label>'
    + `<select id="queue-filter" name="decision">${(['ALL', ...DECISIONS] as QueueFilter[]).map((value) =>
      `<option value="${value}"${value === filter ? ' selected' : ''}>${value === 'ALL' ? 'All decisions' : value}</option>`).join('')}</select>`
    + `<label for="queue-search">Search</label><input id="queue-search" name="query" value="${escapeHtml(query)}">`
    + '<button type="submit">Apply</button></form>';
  const renderedRows = visible.map((item) =>
    `<tr data-story="${escapeHtml(item.storyId)}"><td>${escapeHtml(item.headline)}</td>`
    + `<td>${escapeHtml(item.source)}</td><td class="decision-${item.decision.toLowerCase()}">${item.decision}</td>`
    + `<td>${reviewDetails(item)}</td><td>${controls(item)}</td></tr>`).join('');
  return filterControl + '<table class="producer-queue">'
    + `<caption>Review queue: ${pendingCount(items)} pending</caption>`
    + '<thead><tr><th scope="col">Story</th><th scope="col">Source</th><th scope="col">Decision</th><th scope="col">Review</th>'
    + '<th scope="col">Actions</th></tr></thead>'
    + `<tbody>${renderedRows || '<tr><td colspan="5">No matching stories.</td></tr>'}</tbody></table>`;
}
