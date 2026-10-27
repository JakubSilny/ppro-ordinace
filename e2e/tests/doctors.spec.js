const { test, expect } = require('@playwright/test');
const { login } = require('./helpers');

test('s víc než 4 lékaři je místo tlačítek rozbalovací seznam', async ({ page }) => {
  // k opravdovým lékařům přidá smyšlené jen v odpovědi, databáze zůstane beze změny
  await page.route('**/api/doctors', async (route) => {
    const doctors = await (await route.fetch()).json();
    const extra = ['Adamec', 'Beneš', 'Cibulka'].map((lastName, i) => ({ ...doctors[0], id: 900000 + i, firstName: 'Test', lastName }));
    await route.fulfill({ json: [...doctors, ...extra] });
  });
  await login(page);

  const picker = page.locator('#doctor-picker').getByLabel('Lékař');
  await expect(picker.locator('option')).toHaveCount(6);
  await expect(page.locator('#doctor-picker button')).toHaveCount(0);

  await picker.selectOption({ label: 'MUDr. Vyhlídalová Marie' });
  await expect(page.locator('#diary')).toHaveAttribute('aria-busy', 'false');
  await expect(page.locator('#diary .day').first()).toBeVisible();
  await expect(picker.locator('option:checked')).toHaveText('MUDr. Vyhlídalová Marie');
});
