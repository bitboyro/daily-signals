import { readConfig } from './config.ts';
import { fetchEditions, renderEditionList } from './editions.ts';
import { renderPlayer } from './player.ts';

if (typeof document !== 'undefined') {
  const config = readConfig(window.location.origin);
  const target = document.getElementById('editions');
  if (target) {
    fetchEditions(config.apiBase)
      .then((editions) => {
        target.innerHTML = renderEditionList(editions)
          + (editions.length > 0 ? renderPlayer(config, editions[0]) : '');
      })
      .catch(() => { target.textContent = 'Editions are unavailable right now.'; });
  }
}
