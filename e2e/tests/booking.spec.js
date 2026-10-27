const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

// po přepnutí lékaře nebo týdne počkat na nové načtení diáře, jinak by klik trefil starý týden
async function click(page, name) {
  await page.getByRole('button', { name }).click();
  await expect(page.locator('#diary')).toHaveAttribute('aria-busy', 'false');
}

test('objednání z volného okna, zrušení a uvolnění termínu', async ({ page }, testInfo) => {
  await login(page);
  // každý projekt používá jiného lékaře, aby si testy nesahaly na stejné termíny
  const doctor = testInfo.project.name === 'tablet' ? 'MUDr. Marešová' : 'MUDr. Vyhlídalová';
  await click(page, doctor);
  await click(page, 'Další týden');
  await click(page, 'Další týden');

  const free = page.locator('button.block.free').first();
  await expect(free).toBeVisible();
  const label = await free.getAttribute('aria-label');
  await free.click();

  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Najít pacienta').fill('Kuč');
  await dialog.locator('.list-item').first().click();
  await dialog.getByLabel('Důvod návštěvy').fill('Preventivní prohlídka');
  // podle ID z odpovědi: v diáři může být Kučerová z dřívějšího běhu proti stejné databázi
  const created = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/appointments'
    && r.request().method() === 'POST');
  await dialog.getByRole('button', { name: 'Objednat' }).click();
  await expect(page.getByText(/^Objednáno: /)).toBeVisible();

  const booked = page.locator(`button.block.appt[data-id="${(await (await created).json()).id}"]`);
  await expect(booked).toBeVisible();
  await booked.click();
  await expect(dialog.getByText('Preventivní prohlídka')).toBeVisible();
  await dialog.getByRole('button', { name: 'Zrušit rezervaci' }).click();
  await dialog.getByRole('button', { name: 'Ano, zrušit rezervaci' }).click();
  await expect(page.getByText('Rezervace zrušena, termín je opět volný.')).toBeVisible();
  await expect(page.locator('button.block.free').first()).toHaveAttribute('aria-label', label);
});

test('přesun termínu přes režim výběru', async ({ page }, testInfo) => {
  await login(page);
  const doctor = testInfo.project.name === 'tablet' ? 'MUDr. Marešová' : 'MUDr. Vyhlídalová';
  await click(page, doctor);
  for (let i = 0; i < 3; i++) await click(page, 'Další týden');

  await page.locator('button.block.free').first().click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Najít pacienta').fill('Žák');
  await dialog.locator('.list-item').first().click();
  await dialog.getByRole('button', { name: 'Objednat' }).click();
  await expect(page.getByText(/^Objednáno: /)).toBeVisible();

  await page.locator('button.block.appt', { hasText: 'Žák' }).first().click();
  await dialog.getByRole('button', { name: 'Přesunout' }).click();
  await expect(page.getByText(/^Vyberte nový termín pro/)).toBeVisible();
  await page.getByRole('button', { name: 'Další týden' }).click();
  await page.locator('button.block.free').first().click();
  await dialog.getByRole('button', { name: 'Přesunout termín' }).click();
  await expect(page.getByText(/^Termín přesunut na/)).toBeVisible();
});
