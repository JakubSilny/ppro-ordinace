const { expect } = require('@playwright/test');

const USER = process.env.E2E_USER || 'e2e';
const PASSWORD = process.env.E2E_PASSWORD || 'e2e-password-123';
// správce: musí být v APP_USERS i APP_ADMINS (viz README)
const ADMIN = process.env.E2E_ADMIN || 'e2e-admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'e2e-admin-password-123';

async function login(page, user = USER, password = PASSWORD) {
  await page.goto('/');
  await page.getByLabel('Uživatelské jméno').fill(user);
  await page.getByLabel('Heslo').fill(password);
  await page.getByRole('button', { name: 'Přihlásit se' }).click();
  await expect(page.getByRole('link', { name: 'Diář' })).toBeVisible();
}

/** Unikátní příjmení, aby šly testy pouštět opakovaně proti stejné databázi. */
const unique = (prefix) => `${prefix}${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;

module.exports = { login, unique, USER, PASSWORD, ADMIN, ADMIN_PASSWORD };
