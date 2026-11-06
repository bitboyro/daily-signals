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
  await expect(page.locator('#producer-panel-queue')).toContainText('Approve');
});
