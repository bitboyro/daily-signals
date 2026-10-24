import { escapeHtml } from './html.ts';
import { readEnvelope } from './schema.ts';

export type Decision = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface QueueItem {
  storyId: string;
  headline: string;
  source: string;
  decision: Decision;
}

const DECISIONS: readonly string[] = ['PENDING', 'APPROVED', 'REJECTED'];

function isQueueItem(value: unknown): value is QueueItem {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return typeof v.storyId === 'string' && typeof v.headline === 'string'
    && typeof v.source === 'string' && typeof v.decision === 'string' && DECISIONS.includes(v.decision);
}

export function parseQueue(payload: unknown): QueueItem[] {
  const items = readEnvelope(payload, 'items');
  if (!items.every(isQueueItem)) throw new Error('unexpected review queue response');
  return items;
}

export function pendingCount(items: QueueItem[]): number {
  return items.filter((item) => item.decision === 'PENDING').length;
}

/** Read-only producer view; decisions are still taken in the Java service. */
export function renderQueue(items: QueueItem[]): string {
  const rows = items.map((item) =>
    `<tr data-story="${escapeHtml(item.storyId)}"><td>${escapeHtml(item.headline)}</td>`
    + `<td>${escapeHtml(item.source)}</td><td class="decision-${item.decision.toLowerCase()}">${item.decision}</td></tr>`);
  return '<table class="producer-queue">'
    + `<caption>Review queue: ${pendingCount(items)} pending</caption>`
    + '<thead><tr><th scope="col">Story</th><th scope="col">Source</th><th scope="col">Decision</th></tr></thead>'
    + `<tbody>${rows.join('')}</tbody></table>`;
}
