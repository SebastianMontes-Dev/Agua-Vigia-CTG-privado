import { defineConfig, devices } from '@playwright/test'

const puerto = 4173

export default defineConfig({
  testDir: 'e2e/real',
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: `http://localhost:${puerto}`,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'escritorio',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1280, height: 800 },
        launchOptions: { args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] },
      },
    },
  ],
  webServer: {
    command: process.env.CI ? 'npm run preview' : 'npm run build && npm run preview',
    url: `http://localhost:${puerto}`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
