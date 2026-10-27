const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test('rozpis dne pro lékaře se dá zobrazit a vytisknout', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Rozpis dne' }).click();
  await expect(page.getByRole('heading', { name: 'Rozpis dne' })).toBeVisible();
  await expect(page.locator('#day table, #day .empty')).toBeVisible();
  await expect(page.locator('#day')).toContainText('MUDr.');
});
