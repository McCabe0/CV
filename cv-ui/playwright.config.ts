import { defineConfig, devices } from '@playwright/test'

// Runs through the shell against the whole stack, from the repository root first:
//   docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --wait shell cv-worker job-worker
// The ui, the api, the workers, kafka, postgres and keycloak (with the api's test realm) are all real;
// the LLM is wiremock playing gemini, and the job boards are wiremock playing them, so every run gets
// the same jobs, interview, draft, review and fit summary.
export default defineConfig({
  testDir: './e2e',
  // The journeys save real profiles for the realm's few users, so they run one at a time.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    // WCAG reflow: content must work at 320 css pixels wide without horizontal scrolling.
    {
      name: 'reflow',
      use: { ...devices['Desktop Chrome'], viewport: { width: 320, height: 640 } },
    },
  ],
})
