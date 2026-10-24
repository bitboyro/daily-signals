import { readConfig } from './config.ts';

if (typeof document !== 'undefined') {
  const config = readConfig(window.location.origin);
  document.documentElement.dataset.api = config.apiBase;
}
