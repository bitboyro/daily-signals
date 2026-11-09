import { escapeHtml } from './html.ts';

export type ProducerPanel = 'candidates' | 'script' | 'pronunciations' | 'queue' | 'history' | 'sources';

const PANELS: readonly ProducerPanel[] = ['candidates', 'script', 'pronunciations', 'queue', 'history', 'sources'];

export function isProducerPanel(value: string | undefined): value is ProducerPanel {
  return value !== undefined && PANELS.includes(value as ProducerPanel);
}

export function renderProducerWorkspace(active: ProducerPanel): string {
  const tab = (panel: ProducerPanel, label: string) => {
    const selected = panel === active;
    return `<button type="button" role="tab" id="producer-tab-${panel}" data-producer-panel="${panel}"`
      + ` aria-selected="${selected}" aria-controls="producer-panel-${panel}" tabindex="${selected ? '0' : '-1'}">${escapeHtml(label)}</button>`;
  };
  return '<div class="producer-workspace">'
    + '<div role="tablist" aria-label="Producer workspace">'
    + tab('candidates', 'Today’s candidates') + tab('script', 'Edition script') + tab('pronunciations', 'Pronunciations')
    + tab('queue', 'Review queue') + tab('history', 'Approval history') + tab('sources', 'Source configuration')
    + '</div>'
    + `<div id="producer-panel-${active}" role="tabpanel" aria-labelledby="producer-tab-${active}"></div>`
    + '</div>';
}
