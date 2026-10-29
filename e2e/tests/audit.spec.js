const { test, expect } = require('@playwright/test');
const { login, USER, ADMIN, ADMIN_PASSWORD } = require('./helpers');

test('personál záznam přístupů nevidí ani přes API', async ({ page }) => {
  await login(page);
  await expect(page.getByRole('link', { name: 'Záznam přístupů' })).toBeHidden();
  const today = new Date().toISOString().slice(0, 10);
  const status = await page.evaluate(async (d) => (await fetch(`/api/audit?from=${d}&to=${d}`)).status, today);
  expect(status).toBe(403);
});

test('správce vidí činnosti všech uživatelů i surový API log', async ({ page }) => {
  await login(page); // aby v logu dnes byl i jiný uživatel
  await page.getByRole('link', { name: 'Pacienti' }).click();
  await page.getByRole('button', { name: 'Odhlásit' }).click();

  await login(page, ADMIN, ADMIN_PASSWORD);
  await expect(page.getByText(`${ADMIN} · správce`)).toBeVisible();
  await page.getByRole('link', { name: 'Záznam přístupů' }).click();
  const table = page.locator('#audit table');
  await expect(table.getByRole('cell', { name: USER, exact: true }).first()).toBeVisible();
  await expect(table.getByRole('cell', { name: 'Přihlášení', exact: true }).first()).toBeVisible();

  await page.getByLabel('Zobrazení').selectOption('api');
  await expect(table.locator('th', { hasText: 'Stav' })).toBeVisible();
  await expect(table.getByRole('cell', { name: '/api/audit' }).first()).toBeVisible();

  await page.getByLabel('Uživatel').fill(USER);
  await page.getByRole('button', { name: 'Zobrazit' }).click();
  await expect(table.getByRole('cell', { name: ADMIN, exact: true })).toHaveCount(0);
});
