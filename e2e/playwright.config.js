// @ts-check
const { defineConfig, devices } = require('@playwright/test');

/** Běží proti aplikaci spuštěné přes docker compose (viz README a CI). */
module.exports = defineConfig({
  testDir: './tests',
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    locale: 'cs-CZ',
    timezoneId: 'Europe/Prague',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'tablet', use: { ...devices['iPad (gen 7) landscape'], browserName: 'chromium' } },
    // prohlídka pro klienta: npm run prohlidka (není součástí testů v CI)
    {
      name: 'prohlidka',
      testDir: './prohlidka',
      retries: 0,
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        video: { mode: 'on', size: { width: 1440, height: 900 } },
        launchOptions: { slowMo: Number(process.env.SLOWMO ?? 250) },
        // když chybí tlačítko, spadnout hned s jasnou chybou, ne čekat do konce limitu testu
        actionTimeout: 15_000,
      },
    },
  ],
});
