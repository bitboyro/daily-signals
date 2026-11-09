import { fetchQueue, type QueueItem } from './producer-queue.ts';
import { escapeHtml } from './html.ts';

/** The review queue is the API's current candidate set; rejected items are not eligible for today's edition. */
export async function fetchTodaysCandidates(apiBase: string, fetchImpl: typeof fetch = fetch): Promise<QueueItem[]> {
  return (await fetchQueue(apiBase, fetchImpl)).filter((candidate) => candidate.decision !== 'REJECTED');
}

export function renderTodaysCandidates(candidates: QueueItem[]): string {
  if (candidates.length === 0) return '<p>No usable candidates have arrived today.</p>';
  return '<section class="candidate-deck" aria-labelledby="today-candidates-heading">'
    + `<h3 id="today-candidates-heading">Today’s candidates <span>${candidates.length}</span></h3>`
    + '<p>Approved stories are ready for the edition; pending stories still need review.</p>'
    + '<ol>' + candidates.map((candidate) => '<li>'
      + `<article data-candidate-id="${escapeHtml(candidate.storyId)}" data-decision="${candidate.decision}">`
      + `<h4>${escapeHtml(candidate.headline)}</h4><p>${escapeHtml(candidate.source)}</p>`
      + `<strong>${escapeHtml(candidate.decision.toLowerCase())}</strong></article></li>`).join('') + '</ol></section>';
}
