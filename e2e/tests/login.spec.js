const { test, expect } = require('@playwright/test');
const { login, USER } = require('./helpers');

test('nepřihlášený uživatel skončí na přihlášení a špatné heslo neprojde', async ({ page }) => {
  await page.goto('/#pacienti');
  await expect(page).toHaveURL(/login\.html/);
  await page.getByLabel('Uživatelské jméno').fill(USER);
  await page.getByLabel('Heslo').fill('spatne-heslo');
  await page.getByRole('button', { name: 'Přihlásit se' }).click();
  await expect(page.getByText('Nesprávné jméno nebo heslo.')).toBeVisible();
});

test('přihlášení a odhlášení', async ({ page }) => {
  await login(page);
  await expect(page.getByText(USER, { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Odhlásit' }).click();
  await expect(page.getByText('Byli jste odhlášeni.')).toBeVisible();
  const status = await page.evaluate(async () => (await fetch('/api/doctors')).status);
  expect(status).toBe(401);
});
