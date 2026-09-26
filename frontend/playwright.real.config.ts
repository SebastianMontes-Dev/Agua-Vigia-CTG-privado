import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: 'e2e/real', fullyParallel: false, workers: 1, retries: 0, timeout: 60_000,
  reporter: process.env.CI ? 'github' : 'list',
  use: { ...devices['Desktop Chrome'], baseURL: 'http://localhost:4173', trace: 'on', launchOptions: { args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] } },
  webServer: { command: 'npm run preview', url: 'http://localhost:4173', reuseExistingServer: !process.env.CI, timeout: 120_000 },
})
