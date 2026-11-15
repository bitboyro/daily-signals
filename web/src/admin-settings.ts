import { fetchSetup, renderSetup, saveSetup, setupFromForm, type StationSetup } from './setup.ts';

export async function loadAdminSettings(apiBase: string, fetchImpl: typeof fetch = fetch): Promise<StationSetup> {
  return fetchSetup(apiBase, fetchImpl);
}

export function renderAdminSettings(setup: StationSetup): string {
  return '<section class="admin-settings" aria-labelledby="admin-settings-heading">'
    + '<h3 id="admin-settings-heading">Station settings</h3>'
    + '<p>Update the sources, voice and daily schedule used by the station.</p>'
    + renderSetup(setup) + '</section>';
}

export async function saveAdminSettings(apiBase: string, form: HTMLFormElement, bearer: string,
  fetchImpl: typeof fetch = fetch): Promise<StationSetup> {
  if (!bearer) throw new Error('station sign-in is required to save admin settings');
  return saveSetup(apiBase, setupFromForm(form), bearer, fetchImpl);
}
