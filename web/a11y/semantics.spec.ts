import { expect, test } from '@playwright/test';

test('listener and producer expose labelled landmarks and announcements', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('navigation', { name: 'Sections' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Today’s edition' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Edition archive' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Producer review' })).toBeVisible();

  const audio = page.locator('#player audio');
  await expect(audio).toHaveAttribute('aria-labelledby', /^player-title-/);
  await expect(audio).toHaveAttribute('aria-describedby', /^player-help-/);
  await expect(audio).toHaveAttribute('aria-keyshortcuts', 'Space Enter ArrowLeft ArrowRight N P');
  await expect(page.locator('#player [role="status"]')).toHaveAttribute('aria-atomic', 'true');
  await expect(page.getByRole('navigation', { name: 'Edition chapters' })).toBeVisible();
  await expect(page.getByRole('button', { name: /^Play chapter / }).first()).toBeVisible();

  await expect(page.getByRole('tablist', { name: 'Producer workspace' })).toBeVisible();
  await expect(page.getByLabel('Reviewer token')).toHaveAttribute('aria-describedby', 'reviewer-token-help');

  const duplicateIds = await page.locator('[id]').evaluateAll((elements) => {
    const ids = elements.map((element) => element.id);
    return ids.filter((id, index) => ids.indexOf(id) !== index);
  });
  expect(duplicateIds).toEqual([]);
});
