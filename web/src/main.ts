import { readConfig } from './config.ts';
import { fetchEditions, renderEditionList, type EditionSummary } from './editions.ts';
import { renderPlayer } from './player.ts';
import { fetchQueue, renderQueue, submitDecision, type QueueFilter } from './producer-queue.ts';
import { isProducerPanel, renderProducerWorkspace, type ProducerPanel } from './producer-workspace.ts';
import { renderSourceEditor, validateSources, type SourceConfig } from './source-editor.ts';

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

const DEFAULT_SOURCES: SourceConfig[] = [
  { id: 'public-desk', name: 'Public desk', url: 'https://news.example.invalid/public.xml', priority: 10 },
];

async function showQueue(apiBase: string, target: HTMLElement, filter: QueueFilter = 'ALL', query = ''): Promise<void> {
  const status = element('queue-status');
  try {
    target.innerHTML = renderQueue(await fetchQueue(apiBase), filter, query);
  } catch {
    target.textContent = 'The review queue is unavailable.';
  }
  target.querySelector<HTMLFormElement>('form.queue-filter')?.addEventListener('submit', (event) => {
    event.preventDefault();
    const form = event.currentTarget as HTMLFormElement;
    const nextFilter = form.elements.namedItem('decision') as HTMLSelectElement;
    const nextQuery = form.elements.namedItem('query') as HTMLInputElement;
    void showQueue(apiBase, target, nextFilter.value as QueueFilter, nextQuery.value);
  });
  target.querySelectorAll<HTMLButtonElement>('button[data-action]').forEach((button) => {
    button.addEventListener('click', async () => {
      const token = (element('reviewer-token') as HTMLInputElement).value.trim();
      try {
        const receipt = await submitDecision(apiBase, button.dataset.story!, button.dataset.action as 'APPROVED' | 'REJECTED', token);
        status.textContent = `${receipt.decision} saved for ${receipt.storyId}.`;
        await showQueue(apiBase, target, filter, query);
      } catch (error) {
        status.textContent = error instanceof Error ? error.message : 'Decision failed.';
      }
    });
  });
}

async function showProducerPanel(apiBase: string, panel: ProducerPanel): Promise<void> {
  const workspace = element('producer-workspace');
  workspace.innerHTML = renderProducerWorkspace(panel);
  const target = element(`producer-panel-${panel}`);
  if (panel === 'queue') {
    await showQueue(apiBase, target);
  } else {
    target.innerHTML = renderSourceEditor(DEFAULT_SOURCES);
    const form = target.querySelector<HTMLFormElement>('form.source-editor');
    const status = target.querySelector<HTMLElement>('.source-problems');
    form?.addEventListener('submit', (event) => {
      event.preventDefault();
      const sources = Array.from(form.querySelectorAll<HTMLFieldSetElement>('fieldset[data-source-index]')).map((fieldset) => ({
        id: fieldset.querySelector<HTMLInputElement>('input[name="id"]')!.value,
        name: fieldset.querySelector<HTMLInputElement>('input[name="name"]')!.value,
        url: fieldset.querySelector<HTMLInputElement>('input[name="url"]')!.value,
        priority: Number(fieldset.querySelector<HTMLInputElement>('input[name="priority"]')!.value),
      }));
      const problems = validateSources(sources);
      if (status) status.textContent = problems.length === 0
        ? 'Source configuration preview is valid. Server-side saving arrives with scheduling.'
        : problems.join('. ');
    });
  }
  workspace.querySelectorAll<HTMLButtonElement>('button[data-producer-panel]').forEach((button) => {
    button.addEventListener('click', () => {
      const next = button.dataset.producerPanel;
      if (isProducerPanel(next)) void showProducerPanel(apiBase, next);
    });
  });
}

if (typeof document !== 'undefined') {
  const config = readConfig(window.location.origin);
  const player = element('player');
  void showEditions(config.apiBase, (edition) => { player.innerHTML = renderPlayer(config, edition); });
  void showProducerPanel(config.apiBase, 'queue');
}
