import { defineConfig, devices } from '@playwright/test';

/** TW_PORT runs the suites against a second dev server (e.g. from a worktree while 5173 serves another checkout). */
const PORT = Number(process.env.TW_PORT ?? 5173);
export const APP_ORIGIN = `http://127.0.0.1:${PORT}`;
export const CANARY = 'http://127.0.0.1:9999';

export default defineConfig({
  testDir: 'tests',
  globalSetup: './tests/global-setup.ts',
  testMatch: /.*\.spec\.ts/,
  timeout: 90_000,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: APP_ORIGIN,
    viewport: { width: 1280, height: 900 },
    trace: 'retain-on-failure',
  },
  webServer: [
    { command: `corepack pnpm dev --port ${PORT}`, url: APP_ORIGIN + '/api/corpus', reuseExistingServer: true, timeout: 60_000 },
    { command: 'corepack pnpm canary', url: CANARY + '/__hits', reuseExistingServer: true, timeout: 30_000 },
  ],
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
