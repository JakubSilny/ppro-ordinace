const { expect } = require('@playwright/test');

const USER = process.env.E2E_USER || 'e2e';
const PASSWORD = process.env.E2E_PASSWORD || 'e2e-password-123';

async function login(page) {
  await page.goto('/');
  await page.getByLabel('Uživatelské jméno').fill(USER);
  await page.getByLabel('Heslo').fill(PASSWORD);
  await page.getByRole('button', { name: 'Přihlásit se' }).click();
  await expect(page.getByRole('link', { name: 'Diář' })).toBeVisible();
}

/** Unikátní příjmení, aby šly testy pouštět opakovaně proti stejné databázi. */
const unique = (prefix) => `${prefix}${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;

module.exports = { login, unique, USER, PASSWORD };
