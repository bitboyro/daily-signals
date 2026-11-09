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

test('producer prepares a narrated edition across the complete workspace', async ({ page }) => {
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
