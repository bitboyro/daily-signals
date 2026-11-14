import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { renderChapters } from '../src/chapters.ts';
import { renderArchiveFilter, renderDailyEdition, renderEditionList, type EditionSummary } from '../src/editions.ts';
import { renderNoEditionGuidance, renderPlayer } from '../src/player.ts';

const styles = readFileSync(new URL('../public/styles.css', import.meta.url), 'utf8');
const editions: EditionSummary[] = [
  { editionId: 'morning-2026-11-14', airDate: '2026-11-14', title: 'Morning technology and world briefing', audioPath: '/editions/morning-2026-11-14/audio' },
  { editionId: 'evening-2026-11-13', airDate: '2026-11-13', title: 'Evening climate and culture briefing', audioPath: '/editions/evening-2026-11-13/audio' },
];
const config = { apiBase: 'https://radio.example', streamBase: 'https://stream.example' };

function document(title: string, body: string): string {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${title}</title><style>${styles}</style></head><body><header class="site-header"><div class="station-mark"><span class="signal-dot"></span><div><p>Independent public radio</p><h1>Daily Signals</h1></div></div><nav aria-label="Sections"><a href="#listen">Listen</a><a href="#archive">Archive</a></nav></header><main class="site-main">${body}</main></body></html>`;
}

test('home baseline', async ({ page }) => {
  await page.setContent(document('Daily Signals — Home',
    `<section id="listen"><h2>Today’s edition</h2>${renderDailyEdition(editions[0])}${renderPlayer(config, editions[0])}${renderChapters([
      { title: 'World desk', startMillis: 0 }, { title: 'Technology', startMillis: 95000 }, { title: 'Culture', startMillis: 188000 },
    ])}</section><section id="archive"><h2>Recent editions</h2>${renderEditionList(editions)}</section>`));
  await expect(page).toHaveScreenshot('listener-home.png');
});

test('archive baseline', async ({ page }) => {
  await page.setContent(document('Daily Signals — Archive',
    `<section id="archive"><h2>Edition archive</h2>${renderArchiveFilter('2026-11-14')}${renderEditionList(editions)}</section>`));
  await expect(page).toHaveScreenshot('listener-archive.png');
});

test('player baseline', async ({ page }) => {
  await page.setContent(document('Daily Signals — Player',
    `<section id="listen"><h2>Today’s edition</h2>${renderDailyEdition(editions[0])}${renderPlayer(config, editions[0])}${renderChapters([
      { title: 'World desk', startMillis: 0 }, { title: 'Technology', startMillis: 95000 }, { title: 'Culture', startMillis: 188000 },
    ])}</section>`));
  await expect(page).toHaveScreenshot('listener-player.png');
});

test('empty listener baseline', async ({ page }) => {
  await page.setContent(document('Daily Signals — Waiting for broadcast',
    `<section class="listener-panel" id="listen"><p class="section-kicker">Fresh each morning</p><h2>Today’s edition</h2>${renderNoEditionGuidance()}</section>`
    + `<section class="archive-panel" id="archive"><p class="section-kicker">Listen again</p><h2>Edition archive</h2>${renderArchiveFilter('2026-11-12')}${renderEditionList([], true)}</section>`));
  await expect(page).toHaveScreenshot('listener-empty.png');
});
