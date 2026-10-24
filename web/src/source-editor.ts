import { escapeHtml } from './html.ts';

/** Same rules as SourceRegistry.Source in radio-ingestion and tools/check-source-example.py. */
export interface SourceConfig {
  id: string;
  name: string;
  url: string;
  priority: number;
}

export function validateSources(sources: SourceConfig[]): string[] {
  const problems: string[] = [];
  const seen = new Set<string>();
  sources.forEach((source, index) => {
    const label = `source ${index + 1}`;
    if (!/^[a-z][a-z0-9-]*$/.test(source.id)) problems.push(`${label}: id must be lowercase letters, digits or dashes`);
    else if (seen.has(source.id)) problems.push(`${label}: duplicate id ${source.id}`);
    seen.add(source.id);
    if (source.name.trim() === '') problems.push(`${label}: name is required`);
    let url: URL | undefined;
    try { url = new URL(source.url); } catch { url = undefined; }
    if (!url || url.protocol !== 'https:' || url.hostname === '') problems.push(`${label}: URL must be HTTPS`);
    if (!Number.isInteger(source.priority) || source.priority < 0) problems.push(`${label}: priority must be a nonnegative integer`);
  });
  return problems;
}

export function renderSourceEditor(sources: SourceConfig[]): string {
  const rows = sources.map((source, index) => {
    const field = (name: keyof SourceConfig, label: string, type: string) =>
      `<label for="source-${index}-${name}">${label}</label>`
      + `<input id="source-${index}-${name}" name="${name}" type="${type}" value="${escapeHtml(String(source[name]))}" required>`;
    return `<fieldset data-source-index="${index}"><legend>${escapeHtml(source.name || `Source ${index + 1}`)}</legend>`
      + field('id', 'Identifier', 'text') + field('name', 'Display name', 'text')
      + field('url', 'Feed URL', 'url') + field('priority', 'Priority', 'number') + '</fieldset>';
  });
  return `<form class="source-editor" novalidate>${rows.join('')}`
    + '<div role="status" aria-live="polite" class="source-problems"></div>'
    + '<button type="submit">Save sources</button></form>';
}
