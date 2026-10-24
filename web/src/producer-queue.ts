import { escapeHtml } from './html.ts';

export type Decision = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface QueueItem {
  storyId: string;
  headline: string;
  source: string;
  decision: Decision;
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
