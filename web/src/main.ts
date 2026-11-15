import { readConfig } from './config.ts';
import { editionIdFromSearch, editionSharePath, fetchEditions, renderArchiveFilter, renderDailyEdition, renderEditionList, selectListenerEdition, selectTodaysEdition, type EditionSummary } from './editions.ts';
import { bindPlaybackAnnouncements, bindPlaybackKeyboard, bindPlaybackRecovery, renderNoEditionGuidance, renderPlayer } from './player.ts';
import { fetchQueue, renderQueue, submitDecision, type QueueFilter } from './producer-queue.ts';
import { fetchSchedule, renderFetchSchedule } from './fetch-schedule.ts';
import { isProducerPanel, renderProducerWorkspace, type ProducerPanel } from './producer-workspace.ts';
import { beginProducerSignIn, completeProducerSignIn, producerBearer, signOutProducer } from './producer-auth.ts';
import { fetchTodaysCandidates, renderTodaysCandidates } from './producer-candidates.ts';
import { fetchScript, generateScriptAudio, renderScriptEditor, saveScript } from './producer-script.ts';
import { fetchPronunciations, renderPronunciationEditor, savePronunciations } from './producer-pronunciations.ts';
import { fetchApprovalAudit, pollEditionState, renderApprovalHistory, transitionEdition } from './producer-edition.ts';
import { bindChapterSeeking, fetchChapters, renderChapters } from './chapters.ts';
import { bindPlaybackPosition } from './playback-position.ts';
import { cacheEditionForOffline, registerServiceWorker } from './pwa.ts';
import { localeTag, type Locale } from './i18n.ts';
import { fetchSetup, renderSetup, saveSetup, setupFromForm } from './setup.ts';
import { loadAdminSettings, renderAdminSettings, saveAdminSettings } from './admin-settings.ts';

function element(id: string): HTMLElement {
  const found = document.getElementById(id);
  if (!found) throw new Error(`listener page is missing #${id}`);
  return found;
}

async function showEditions(apiBase: string, locale: Locale, play: (edition: EditionSummary) => void, airDate = '', requestedId?: string,
  retainOffline?: (edition: EditionSummary) => void, showEmpty?: () => void): Promise<void> {
  const target = element('editions');
  try {
    const editions = await fetchEditions(apiBase, fetch, airDate || undefined);
    target.innerHTML = renderArchiveFilter(airDate) + renderEditionList(editions, Boolean(airDate), locale);
    target.querySelector<HTMLFormElement>('form.archive-filter')?.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = event.currentTarget as HTMLFormElement;
      const date = (form.elements.namedItem('date') as HTMLInputElement).value;
      void showEditions(apiBase, locale, play, date, undefined, retainOffline, showEmpty);
    });
    target.querySelector<HTMLButtonElement>('button[data-clear-date]')?.addEventListener('click', () => {
      void showEditions(apiBase, locale, play, '', undefined, retainOffline, showEmpty);
    });
    target.querySelectorAll<HTMLAnchorElement>('a[data-edition]').forEach((link) => {
      link.addEventListener('click', (event) => {
        event.preventDefault();
        const chosen = editions.find((edition) => edition.editionId === link.dataset.edition);
        if (chosen) {
          history.replaceState({}, '', editionSharePath(chosen.editionId));
          play(chosen);
        }
      });
    });
    const selected = selectListenerEdition(editions, requestedId);
    if (selected) play(selected);
    else if (!airDate) showEmpty?.();
    if (!airDate) {
      const daily = selectTodaysEdition(editions);
      if (daily) retainOffline?.(daily);
    }
  } catch {
    target.innerHTML = '<div class="empty archive-empty"><strong>The archive is temporarily unavailable.</strong><p>Your saved edition may still be playable offline. Please try the archive again shortly.</p></div>';
  }
}

async function showQueue(apiBase: string, target: HTMLElement, locale: Locale, filter: QueueFilter = 'ALL', query = ''): Promise<void> {
  const status = element('queue-status');
  try {
    const [items, schedule] = await Promise.all([fetchQueue(apiBase), fetchSchedule(apiBase)]);
    target.innerHTML = renderFetchSchedule(schedule, localeTag(locale)) + renderQueue(items, filter, query);
  } catch {
    target.textContent = 'The review queue is unavailable.';
  }
  target.querySelector<HTMLFormElement>('form.queue-filter')?.addEventListener('submit', (event) => {
    event.preventDefault();
    const form = event.currentTarget as HTMLFormElement;
    const nextFilter = form.elements.namedItem('decision') as HTMLSelectElement;
    const nextQuery = form.elements.namedItem('query') as HTMLInputElement;
    void showQueue(apiBase, target, locale, nextFilter.value as QueueFilter, nextQuery.value);
  });
  target.querySelectorAll<HTMLButtonElement>('button[data-action]').forEach((button) => {
    button.addEventListener('click', async () => {
      const token = producerBearer(sessionStorage, (element('reviewer-token') as HTMLInputElement).value);
      try {
        const receipt = await submitDecision(apiBase, button.dataset.story!, button.dataset.action as 'APPROVED' | 'REJECTED', token);
        status.textContent = `${receipt.decision} saved for ${receipt.storyId}.`;
        await showQueue(apiBase, target, locale, filter, query);
      } catch (error) {
        status.textContent = error instanceof Error ? error.message : 'Decision failed.';
      }
    });
  });
}

async function showProducerPanel(apiBase: string, panel: ProducerPanel, locale: Locale): Promise<void> {
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
      target.querySelector<HTMLButtonElement>('button[data-generate-audio]')?.addEventListener('click', () => {
        const status = target.querySelector<HTMLElement>('.script-status');
        const player = target.querySelector<HTMLAudioElement>('audio[data-audio-preview]');
        void generateScriptAudio(apiBase, producerBearer(sessionStorage,
          (element('reviewer-token') as HTMLInputElement).value)).then((audio) => {
          if (!player) return;
          if (player.src.startsWith('blob:')) URL.revokeObjectURL(player.src);
          player.src = URL.createObjectURL(audio.audio);
          player.hidden = false;
          const validation = target.querySelector<HTMLElement>('.audio-validation');
          if (validation) validation.textContent = `Validated ${audio.sampleRate} Hz, ${audio.channels} channel, ${(audio.durationMillis / 1000).toFixed(1)} seconds.`;
          if (status) status.textContent = 'Audio preview generated from the saved script.';
        }).catch((error) => {
          if (status) status.textContent = error instanceof Error ? error.message : 'Audio generation failed.';
        });
      });
      target.querySelectorAll<HTMLButtonElement>('button[data-edition-action]').forEach((button) => {
        button.addEventListener('click', () => {
          const action = button.dataset.editionAction as 'APPROVE' | 'PUBLISH';
          const status = target.querySelector<HTMLElement>('.script-status');
          const token = producerBearer(sessionStorage, (element('reviewer-token') as HTMLInputElement).value);
          void transitionEdition(apiBase, token, action).then(async (state) => {
            if (status) status.textContent = `Edition is ${state.status.toLowerCase()} at revision ${state.revision}.`;
            if (action === 'PUBLISH' && state.status !== 'PUBLISHED') {
              const terminal = await pollEditionState(apiBase, token);
              if (status) status.textContent = terminal.status === 'PUBLISHED'
                ? `Edition published at revision ${terminal.revision}.` : 'Edition publication failed.';
            }
          }).catch((error) => {
            if (status) status.textContent = error instanceof Error ? error.message : 'Edition action failed.';
          });
        });
      });
    } catch (error) {
      target.textContent = error instanceof Error ? error.message : 'The edition script is unavailable.';
    }
  } else if (panel === 'pronunciations') {
    const token = producerBearer(sessionStorage, (element('reviewer-token') as HTMLInputElement).value);
    try {
      const draft = await fetchPronunciations(apiBase, token);
      target.innerHTML = renderPronunciationEditor(draft);
      target.querySelector<HTMLFormElement>('form.producer-pronunciations')?.addEventListener('submit', (event) => {
        event.preventDefault();
        const form = event.currentTarget as HTMLFormElement;
        const status = form.querySelector<HTMLElement>('.pronunciation-status');
        const text = (form.elements.namedItem('entries') as HTMLTextAreaElement).value;
        void savePronunciations(apiBase, producerBearer(sessionStorage,
          (element('reviewer-token') as HTMLInputElement).value), text).then((saved) => {
          if (status) status.textContent = `Pronunciation revision ${saved.revision} saved.`;
        }).catch((error) => {
          if (status) status.textContent = error instanceof Error ? error.message : 'Save failed.';
        });
      });
    } catch (error) {
      target.textContent = error instanceof Error ? error.message : 'Pronunciations are unavailable.';
    }
  } else if (panel === 'queue') {
    await showQueue(apiBase, target, locale);
  } else if (panel === 'history') {
    const token = producerBearer(sessionStorage, (element('reviewer-token') as HTMLInputElement).value);
    try {
      target.innerHTML = renderApprovalHistory(await fetchApprovalAudit(apiBase, token), locale);
    } catch (error) {
      target.textContent = error instanceof Error ? error.message : 'Approval history is unavailable.';
    }
  } else if (panel === 'settings') {
    try {
      target.innerHTML = renderAdminSettings(await loadAdminSettings(apiBase));
    } catch {
      target.textContent = 'Station settings are unavailable.';
      return;
    }
    target.querySelector<HTMLFormElement>('form.station-setup')?.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = event.currentTarget as HTMLFormElement;
      const status = form.querySelector<HTMLElement>('.setup-status');
      const token = producerBearer(sessionStorage, (element('reviewer-token') as HTMLInputElement).value);
      void saveAdminSettings(apiBase, form, token).then(() => {
        if (status) status.textContent = 'Station settings saved.';
      }).catch((error) => {
        if (status) status.textContent = error instanceof Error ? error.message : 'Settings save failed.';
      });
    });
  }
  workspace.querySelectorAll<HTMLButtonElement>('button[data-producer-panel]').forEach((button) => {
    button.addEventListener('click', () => {
      const next = button.dataset.producerPanel;
      if (isProducerPanel(next)) void showProducerPanel(apiBase, next, locale);
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
      if (isProducerPanel(selected)) void showProducerPanel(apiBase, selected, locale);
    });
  });
}

async function showStationSetup(apiBase: string): Promise<void> {
  const target = element('station-setup');
  try {
    const setup = await fetchSetup(apiBase);
    target.innerHTML = renderSetup(setup);
    target.querySelector<HTMLFormElement>('form.station-setup')?.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = event.currentTarget as HTMLFormElement;
      const status = form.querySelector<HTMLElement>('.setup-status');
      try {
        const next = setupFromForm(form);
        const token = producerBearer(sessionStorage, (element('reviewer-token') as HTMLInputElement).value);
        void saveSetup(apiBase, next, setup.configured ? token : '').then(() => {
          if (status) status.textContent = 'Station setup saved. The next fetch uses this schedule.';
        }).catch((error) => { if (status) status.textContent = error instanceof Error ? error.message : 'Setup save failed.'; });
      } catch (error) {
        if (status) status.textContent = error instanceof Error ? error.message : 'Setup is invalid.';
      }
    });
  } catch {
    target.textContent = 'Station setup is unavailable.';
  }
}

if (typeof document !== 'undefined') {
  void registerServiceWorker().catch(() => { /* Installation is optional; listening stays available. */ });
  const runtime = (window as Window & { DAILY_SIGNALS_CONFIG?: Parameters<typeof readConfig>[1] }).DAILY_SIGNALS_CONFIG;
  const config = readConfig(window.location.origin, runtime);
  document.documentElement.lang = config.locale;
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
  void showEditions(config.apiBase, config.locale, (edition) => {
    player.innerHTML = renderDailyEdition(edition, config.locale) + renderPlayer(config, edition);
    bindPlaybackAnnouncements(player, edition.title);
    bindPlaybackRecovery(player);
    bindPlaybackKeyboard(player);
    bindPlaybackPosition(player, edition.editionId);
    void fetchChapters(config.streamBase, edition.editionId).then((chapters) => {
      player.insertAdjacentHTML('beforeend', renderChapters(chapters));
      bindChapterSeeking(player);
    }).catch(() => { /* v1/v2 editions remain playable without chapter metadata. */ });
  }, '', editionIdFromSearch(window.location.search), (edition) => {
    void cacheEditionForOffline(config, edition).catch(() => { /* Online playback remains available. */ });
  }, () => {
    player.innerHTML = renderNoEditionGuidance();
  });
  void showProducerPanel(config.apiBase, 'candidates', config.locale);
  void showStationSetup(config.apiBase);
}
