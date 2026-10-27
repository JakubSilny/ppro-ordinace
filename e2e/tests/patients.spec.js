const { test, expect } = require('@playwright/test');
const { login, unique } = require('./helpers');

test('nový pacient, nalezení podle telefonu a hlídání duplicity', async ({ page }) => {
  await login(page);
  const lastName = unique('Testová');
  const phone = `6${String(Date.now()).slice(-8)}`;

  await page.getByRole('link', { name: 'Pacienti' }).click();
  await page.getByRole('button', { name: 'Nový pacient' }).click();
  const dialog = page.getByRole('dialog');
  const fill = async () => {
    await dialog.getByLabel('Jméno', { exact: true }).fill('Alena');
    await dialog.getByLabel('Příjmení').fill(lastName);
    await dialog.getByLabel('Rok narození').fill('1985');
    await dialog.getByLabel('Telefon').fill(phone);
  };
  await fill();
  await dialog.getByRole('button', { name: 'Založit pacienta' }).click();
  await expect(page.getByRole('heading', { name: `${lastName} Alena` })).toBeVisible();

  await page.getByRole('button', { name: 'Nový pacient' }).click();
  await fill();
  await dialog.getByRole('button', { name: 'Založit pacienta' }).click();
  await expect(dialog.getByText(/už v evidenci je/)).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Otevřít existujícího' })).toBeVisible();
  await dialog.getByRole('button', { name: 'Zavřít' }).click();

  await page.getByLabel('Najít pacienta').fill(phone.slice(0, 6));
  await expect(page.locator('#global-results').getByText(`${lastName} Alena`)).toBeVisible();
});

test('výkaz a přehled rušení se zobrazí', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Výkazy' }).click();
  await page.getByRole('button', { name: 'Zobrazit' }).click();
  await expect(page.locator('#report')).not.toBeEmpty();
  await page.getByRole('link', { name: 'Rušení' }).click();
  await expect(page.getByRole('heading', { name: 'Kdo ruší nebo nechodí' })).toBeVisible();
  await expect(page.locator('#stats')).not.toBeEmpty();
});
