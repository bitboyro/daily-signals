import { readConfig } from './config.ts';
import { fetchEditions, renderEditionList, type EditionSummary } from './editions.ts';
import { renderPlayer } from './player.ts';
import { fetchQueue, renderQueue, submitDecision } from './producer-queue.ts';

function element(id: string): HTMLElement {
  const found = document.getElementById(id);
  if (!found) throw new Error(`listener page is missing #${id}`);
  return found;
}

async function showEditions(apiBase: string, play: (edition: EditionSummary) => void): Promise<void> {
  const target = element('editions');
  try {
    const editions = await fetchEditions(apiBase);
    target.innerHTML = renderEditionList(editions);
    target.querySelectorAll<HTMLAnchorElement>('a[data-edition]').forEach((link) => {
      link.addEventListener('click', () => {
        const chosen = editions.find((edition) => edition.editionId === link.dataset.edition);
        if (chosen) play(chosen);
      });
    });
    if (editions.length > 0) play(editions[0]);
  } catch {
    target.textContent = 'Editions are unavailable right now.';
  }
}

async function showQueue(apiBase: string): Promise<void> {
  const target = element('queue');
  const status = element('queue-status');
  try {
    target.innerHTML = renderQueue(await fetchQueue(apiBase));
  } catch {
    target.textContent = 'The review queue is unavailable.';
  }
  target.querySelectorAll<HTMLButtonElement>('button[data-action]').forEach((button) => {
    button.addEventListener('click', async () => {
      const token = (element('reviewer-token') as HTMLInputElement).value.trim();
      try {
        await submitDecision(apiBase, button.dataset.story!, button.dataset.action as 'APPROVED' | 'REJECTED', token);
        status.textContent = 'Decision saved.';
        await showQueue(apiBase);
      } catch (error) {
        status.textContent = error instanceof Error ? error.message : 'Decision failed.';
      }
    });
  });
}

if (typeof document !== 'undefined') {
  const config = readConfig(window.location.origin);
  const player = element('player');
  void showEditions(config.apiBase, (edition) => { player.innerHTML = renderPlayer(config, edition); });
  void showQueue(config.apiBase);
}
