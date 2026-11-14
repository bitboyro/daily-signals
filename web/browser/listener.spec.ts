import { expect, test } from '@playwright/test';

test('listener archive, audio and producer queue work in a real browser', async ({ page, request }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Daily Signals' })).toBeVisible();
  const edition = page.locator('#editions a[data-edition]').first();
  await expect(edition).toBeVisible();
  await edition.click();

  const audio = page.locator('#player audio');
  await expect(audio).toBeVisible();
  const source = await audio.getAttribute('src');
  expect(source).toBeTruthy();
  const response = await request.get(source!);
  expect(response.ok()).toBeTruthy();
  expect(response.headers()['content-type']).toContain('audio/wav');

  await expect(page.getByRole('heading', { name: 'Producer review' })).toBeVisible();
  await page.getByRole('tab', { name: 'Review queue' }).click();
  await expect(page.locator('#producer-panel-queue')).toContainText('Approve');
});

test('listener plays, seeks, resumes and replays today\'s edition offline', async ({ page, context }) => {
  await page.goto('/');
  const audio = page.locator('#player audio');
  await expect(audio).toBeVisible();
  await page.waitForFunction(async () => {
    await navigator.serviceWorker.ready;
    const cache = await caches.open('daily-signals-edition-v1');
    return (await cache.keys()).length === 4;
  });

  await audio.evaluate(async (element) => {
    element.muted = true;
    await element.play();
    element.pause();
    element.play = async () => { (window as Window & { chapterPlayRequested?: boolean }).chapterPlayRequested = true; };
  });
  await page.getByRole('button', { name: /Local report/ }).click();
  const chapterState = await audio.evaluate((element) => ({
    currentTime: element.currentTime,
    duration: element.duration,
    playRequested: (window as Window & { chapterPlayRequested?: boolean }).chapterPlayRequested,
  }));
  expect(chapterState.playRequested).toBe(true);
  expect(chapterState.currentTime).toBeGreaterThanOrEqual(0.6);
  await audio.evaluate((element) => element.dispatchEvent(new Event('timeupdate')));

  await page.reload();
  const resumed = page.locator('#player audio');
  await expect(resumed).toBeVisible();
  await resumed.evaluate((element) => element.load());
  await expect.poll(() => resumed.evaluate((element) => element.currentTime)).toBeGreaterThanOrEqual(0.6);
  await context.setOffline(true);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Daily Signals' })).toBeVisible();
  const offline = await page.locator('#player audio').evaluate(async (element) => {
    const response = await fetch(element.src);
    return { ok: response.ok, type: response.headers.get('content-type'), bytes: (await response.arrayBuffer()).byteLength };
  });
  expect(offline.ok).toBeTruthy();
  expect(offline.type).toContain('audio/wav');
  expect(offline.bytes).toBeGreaterThan(44);
  await context.setOffline(false);
});

test('producer prepares a narrated edition across the complete workspace', async ({ page, browserName }, testInfo) => {
  test.skip(browserName !== 'chromium' || testInfo.project.name !== 'chromium-desktop',
    'the stateful producer journey runs once; the listener journey owns the cross-browser matrix');
  await page.goto('/');
  await page.getByLabel('Reviewer token').fill('nightly-producer-token-0001');

  await expect(page.getByRole('heading', { name: /Today’s candidates/ })).toBeVisible();

  await page.getByRole('tab', { name: 'Edition script' }).click();
  const script = page.getByRole('textbox', { name: 'Edition script' });
  await expect(script).toBeVisible();
  await script.fill('Daily Signals. SQL and AI are in today’s technology bulletin.');
  await page.getByRole('button', { name: 'Save script' }).click();
  await expect(page.getByRole('status').filter({ hasText: /Script revision 1 saved/ })).toBeVisible();

  await page.getByRole('tab', { name: 'Pronunciations' }).click();
  const pronunciations = page.getByLabel('Pronunciation fixes');
  await expect(pronunciations).toBeVisible();
  await pronunciations.fill('SQL=sequel\nAI=A I');
  await page.getByRole('button', { name: 'Save pronunciations' }).click();
  await expect(page.getByRole('status').filter({ hasText: /Pronunciation revision 1 saved/ })).toBeVisible();

  await page.getByRole('tab', { name: 'Review queue' }).click();
  const approve = page.getByRole('button', { name: /^Approve / }).first();
  await expect(approve).toBeVisible();
  await approve.click();
  await expect(page.locator('#queue-status')).toContainText('APPROVED saved');

  await page.getByRole('tab', { name: 'Edition script' }).click();
  await page.getByRole('button', { name: 'Generate audio preview' }).click();
  const preview = page.locator('audio[data-audio-preview]');
  await expect(preview).toBeVisible();
  await expect(preview).toHaveAttribute('src', /^blob:/);
  await expect(page.getByRole('status').filter({ hasText: /Audio preview generated/ })).toBeVisible();

  await page.getByRole('button', { name: 'Approve edition' }).click();
  await expect(page.getByRole('status').filter({ hasText: /Edition is ready at revision 1/ })).toBeVisible();
  await page.getByRole('tab', { name: 'Approval history' }).click();
  await expect(page.getByRole('heading', { name: 'Approval history' })).toBeVisible();
  await expect(page.locator('ol.approval-history')).toContainText('PRODUCER approved revision 1');
  await page.getByRole('tab', { name: 'Edition script' }).click();
  await page.getByRole('button', { name: 'Publish edition' }).click();
  await expect(page.getByRole('status').filter({ hasText: /Edition is published at revision 2/ })).toBeVisible();
});
