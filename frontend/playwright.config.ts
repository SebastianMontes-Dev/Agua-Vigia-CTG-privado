import { defineConfig, devices } from '@playwright/test'

const puerto = 4173

export default defineConfig({
  testDir: 'e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: `http://localhost:${puerto}`,
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'movil', use: { ...devices['Pixel 7'], viewport: { width: 360, height: 780 } } },
    { name: 'escritorio', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
  ],
  webServer: {
    command: process.env.CI ? 'npm run preview' : 'npm run build && npm run preview',
    url: `http://localhost:${puerto}`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
