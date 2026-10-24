import { readConfig } from './config.ts';
import { fetchEditions, renderEditionList } from './editions.ts';

if (typeof document !== 'undefined') {
  const config = readConfig(window.location.origin);
  const target = document.getElementById('editions');
  if (target) {
    fetchEditions(config.apiBase)
      .then((editions) => { target.innerHTML = renderEditionList(editions); })
      .catch(() => { target.textContent = 'Editions are unavailable right now.'; });
  }
}
