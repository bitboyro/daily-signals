import { readConfig } from './config.ts';
import { fetchEditions, renderEditionList, type EditionSummary } from './editions.ts';
import { bindPlaybackRecovery, renderPlayer } from './player.ts';
import { fetchQueue, renderQueue, submitDecision, type QueueFilter } from './producer-queue.ts';
import { fetchSchedule, renderFetchSchedule } from './fetch-schedule.ts';
import { isProducerPanel, renderProducerWorkspace, type ProducerPanel } from './producer-workspace.ts';
import { renderSourceEditor, validateSources, type SourceConfig } from './source-editor.ts';
import { beginProducerSignIn, completeProducerSignIn, producerBearer, signOutProducer } from './producer-auth.ts';
import { fetchTodaysCandidates, renderTodaysCandidates } from './producer-candidates.ts';
import { fetchScript, renderScriptEditor, saveScript } from './producer-script.ts';

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
    const [items, schedule] = await Promise.all([fetchQueue(apiBase), fetchSchedule(apiBase)]);
    target.innerHTML = renderFetchSchedule(schedule) + renderQueue(items, filter, query);
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
      const token = producerBearer(sessionStorage, (element('reviewer-token') as HTMLInputElement).value);
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
  if (panel === 'candidates') {
    try {
      target.innerHTML = renderTodaysCandidates(await fetchTodaysCandidates(apiBase));
    } catch {
      target.textContent = 'Today’s candidates are unavailable.';
    }
  } else if (panel === 'script') {
    const token = producerBearer(sessionStorage, (element('reviewer-token') as HTMLInputElement).value);
    try {
      const draft = await fetchScript(apiBase, token);
      target.innerHTML = renderScriptEditor(draft);
      target.querySelector<HTMLFormElement>('form.producer-script')?.addEventListener('submit', (event) => {
        event.preventDefault();
        const form = event.currentTarget as HTMLFormElement;
        const status = form.querySelector<HTMLElement>('.script-status');
        const text = (form.elements.namedItem('script') as HTMLTextAreaElement).value;
        void saveScript(apiBase, producerBearer(sessionStorage,
          (element('reviewer-token') as HTMLInputElement).value), text).then((saved) => {
          if (status) status.textContent = `Script revision ${saved.revision} saved.`;
        }).catch((error) => { if (status) status.textContent = error instanceof Error ? error.message : 'Script save failed.'; });
      });
    } catch (error) {
      target.textContent = error instanceof Error ? error.message : 'The edition script is unavailable.';
    }
  } else if (panel === 'queue') {
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
    button.addEventListener('keydown', (event) => {
      if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      const tabs = Array.from(workspace.querySelectorAll<HTMLButtonElement>('button[data-producer-panel]'));
      const index = tabs.indexOf(button);
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1
        : (index + (event.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length;
      tabs[next].focus();
      const selected = tabs[next].dataset.producerPanel;
      if (isProducerPanel(selected)) void showProducerPanel(apiBase, selected);
    });
  });
}

if (typeof document !== 'undefined') {
  const runtime = (window as Window & { DAILY_SIGNALS_CONFIG?: Parameters<typeof readConfig>[1] }).DAILY_SIGNALS_CONFIG;
  const config = readConfig(window.location.origin, runtime);
  const signIn = element('producer-sign-in') as HTMLButtonElement;
  const signOut = element('producer-sign-out') as HTMLButtonElement;
  const authStatus = element('producer-auth-status');
  if (config.producerOidc) {
    signIn.hidden = false;
    void completeProducerSignIn(window.location.href, config.producerOidc, sessionStorage).then((token) => {
      if (token) authStatus.textContent = 'Signed in with the station identity provider.';
      if (window.location.search.includes('code=')) history.replaceState({}, '', config.producerOidc!.redirectUri);
    }).catch((error) => { authStatus.textContent = error instanceof Error ? error.message : 'Sign-in failed.'; });
    signIn.addEventListener('click', () => {
      void beginProducerSignIn(config.producerOidc!, sessionStorage).then((target) => window.location.assign(target))
        .catch((error) => { authStatus.textContent = error instanceof Error ? error.message : 'Sign-in failed.'; });
    });
    signOut.addEventListener('click', () => {
      signOutProducer(sessionStorage);
      authStatus.textContent = 'Signed out. A local reviewer token can still be used.';
    });
  }
  const player = element('player');
  void showEditions(config.apiBase, (edition) => {
    player.innerHTML = renderPlayer(config, edition);
    bindPlaybackRecovery(player);
  });
  void showProducerPanel(config.apiBase, 'candidates');
}
